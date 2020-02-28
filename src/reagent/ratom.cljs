(ns reagent.ratom
  (:refer-clojure :exclude [atom])
  (:require-macros [reagent.ratom])
  (:require [reagent.impl.util :as util]
            [reagent.debug :refer-macros [warn dev?]]
            [reagent.impl.batching :as batch]
            [clojure.set :as s]
            [goog.object :as obj]))

(declare ^:dynamic *ratom-context*)
(defonce ^boolean debug false)
(defonce ^:private generation 0)
(defonce ^:private -running (clojure.core/atom 0))

(defn ^boolean reactive? []
  (some? *ratom-context*))


;;; Utilities

(defn running []
  (+ @-running))

(defn- ^number arr-len [x]
  (if (nil? x) 0 (alength x)))

(defn- ^boolean arr-eq [x y]
  (let [len (arr-len x)]
    (and (== len (arr-len y))
         (loop [i 0]
           (or (== i len)
               (if (identical? (aget x i) (aget y i))
                 (recur (inc i))
                 false))))))

(defn- in-context
  "When f is executed, if (f) derefs any ratoms, they are then added to 'obj.captured'(*ratom-context*).

  See function notify-deref-watcher! to know how *ratom-context* is updated"
  [obj f]
  (binding [*ratom-context* obj]
    (f)))

(defn- deref-capture
  "Returns `(in-context f r)`.  Calls `_update-watching` on r with any
  `deref`ed atoms captured during `in-context`, if any differ from the
  `watching` field of r.  Clears the `dirty?` flag on r.

  Inside '_update-watching' along with adding the ratoms in 'r.watching' of reaction,
  the reaction is also added to the list of watches on each ratoms f derefs."
  [f ^clj r]
  (set! (.-captured r) nil)
  (when (dev?)
    (set! (.-ratomGeneration r) (set! generation (inc generation))))
  (let [res (in-context r f)
        c (.-captured r)]
    (set! (.-dirty? r) false)
    ;; Optimize common case where derefs occur in same order
    (when-not (arr-eq c (.-watching r))
      (._update-watching r c))
    res))

(defn- notify-deref-watcher!
  "Add `derefed` to the `captured` field of `*ratom-context*`.

  See also `in-context`"
  [derefed]
  (when-some [r *ratom-context*]
    (let [c (.-captured r)]
      (if (nil? c)
        (set! (.-captured r) (array derefed))
        (.push c derefed)))))

(defn- check-watches [old new]
  (when debug
    (swap! -running + (- (count new) (count old))))
  new)

(defn- add-w [^clj this key f]
  (let [w (.-watches this)]
    (set! (.-watches this) (check-watches w (assoc w key f)))
    (set! (.-watchesArr this) nil)))

(defn- remove-w [^clj this key]
  (let [w (.-watches this)]
    (set! (.-watches this) (check-watches w (dissoc w key)))
    (set! (.-watchesArr this) nil)))

(defn- notify-w [^clj this old new]
  (let [w (.-watchesArr this)
        a (if (nil? w)
            ;; Copy watches to array for speed
            (->> (.-watches this)
                 (reduce-kv #(doto %1 (.push %2) (.push %3)) #js[])
                 (set! (.-watchesArr this)))
            w)
        len (alength a)]
    (loop [i 0]
      (when (< i len)
        (let [k (aget a i)
              f (aget a (inc i))]
          (f k this old new))
        (recur (+ 2 i))))))

(defn- pr-atom [a writer opts s v]
  (-write writer (str "#object[reagent.ratom." s " "))
  (pr-writer (binding [*ratom-context* nil] v) writer opts)
  (-write writer "]"))


;;; Queueing

(defonce ^:private rea-queue nil)

(defn- rea-enqueue [r]
  (when (nil? rea-queue)
    (set! rea-queue (array))
    (batch/schedule))
  (.push rea-queue r))

(defn flush! []
  (loop []
    (let [q rea-queue]
      (when-not (nil? q)
        (set! rea-queue nil)
        (dotimes [i (alength q)]
          (._queued-run (aget q i)))
        (recur)))))

(set! batch/ratom-flush flush!)


;;; Atom

(defprotocol IReactiveAtom)

(deftype RAtom [^:mutable state meta validator ^:mutable watches]
  IAtom
  IReactiveAtom

  IEquiv
  (-equiv [o other] (identical? o other))

  IDeref
  (-deref [this]
    (notify-deref-watcher! this)
    state)

  IReset
  (-reset! [a new-value]
    (when-not (nil? validator)
      (assert (validator new-value) "Validator rejected reference state"))
    (let [old-value state]
      (set! state new-value)
      (when-not (nil? watches)
        (notify-w a old-value new-value))
      new-value))

  ISwap
  (-swap! [a f]          (-reset! a (f state)))
  (-swap! [a f x]        (-reset! a (f state x)))
  (-swap! [a f x y]      (-reset! a (f state x y)))
  (-swap! [a f x y more] (-reset! a (apply f state x y more)))

  IWithMeta
  (-with-meta [_ new-meta] (RAtom. state new-meta validator watches))

  IMeta
  (-meta [_] meta)

  IPrintWithWriter
  (-pr-writer [a w opts] (pr-atom a w opts "RAtom" {:val (-deref a)}))

  IWatchable
  (-notify-watches [this old new] (notify-w this old new))
  (-add-watch [this key f]        (add-w this key f))
  (-remove-watch [this key]       (remove-w this key))

  IHash
  (-hash [this] (goog/getUid this)))

(defn atom
  "Like clojure.core/atom, except that it keeps track of derefs."
  ([x] (->RAtom x nil nil nil))
  ([x & {:keys [meta validator]}] (->RAtom x meta validator nil)))


;;; track

(declare make-reaction)

(defn- cached-reaction [f ^clj o k ^clj obj destroy]
  (let [m (.-reagReactionCache o)
        m (if (nil? m) {} m)
        r (m k nil)]
    (cond
      (some? r) (-deref r)
      (nil? *ratom-context*) (f)
      :else (let [r (make-reaction
                     f :on-dispose (fn [x]
                                     (when debug (swap! -running dec))
                                     (as-> (.-reagReactionCache o) _
                                       (dissoc _ k)
                                       (set! (.-reagReactionCache o) _))
                                     (when (some? obj)
                                       (set! (.-reaction obj) nil))
                                     (when (some? destroy)
                                       (destroy x))))
                  v (-deref r)]
              (set! (.-reagReactionCache o) (assoc m k r))
              (when debug (swap! -running inc))
              (when (some? obj)
                (set! (.-reaction obj) r))
              v))))

(deftype Track [f args ^:mutable reaction]
  IReactiveAtom

  IDeref
  (-deref [this]
    (if-some [r reaction]
      (-deref r)
      (cached-reaction #(apply f args) f args this nil)))

  IEquiv
  (-equiv [_ ^clj other]
    (and (instance? Track other)
         (= f (.-f other))
         (= args (.-args other))))

  IHash
  (-hash [_] (hash [f args]))

  IPrintWithWriter
  (-pr-writer [a w opts] (pr-atom a w opts "Track" {:val (-deref a)
                                                    :f f})))

(defn make-track [f args]
  (Track. f args nil))

(defn make-track! [f args]
  (let [t (make-track f args)
        r (make-reaction #(-deref t)
                         :auto-run true)]
    @r
    r))

(defn track [f & args]
  {:pre [(ifn? f)]}
  (make-track f args))

(defn track! [f & args]
  {:pre [(ifn? f)]}
  (make-track! f args))

;;; cursor

(deftype RCursor [ratom path ^:mutable reaction
                  ^:mutable state ^:mutable watches]
  IAtom
  IReactiveAtom

  IEquiv
  (-equiv [_ ^clj other]
    (and (instance? RCursor other)
         (= path (.-path other))
         (= ratom (.-ratom other))))

  Object
  (_peek [this]
    (binding [*ratom-context* nil]
      (-deref this)))

  (_set-state [this oldstate newstate]
    (when-not (identical? oldstate newstate)
      (set! state newstate)
      (when (some? watches)
        (notify-w this oldstate newstate))))

  IDeref
  (-deref [this]
    (let [oldstate state
          newstate (if-some [r reaction]
                     (-deref r)
                     (let [f (if (satisfies? IDeref ratom)
                               #(get-in @ratom path)
                               #(ratom path))]
                       (cached-reaction f ratom path this nil)))]
      (._set-state this oldstate newstate)
      newstate))

  IReset
  (-reset! [this new-value]
    (let [oldstate state]
      (._set-state this oldstate new-value)
      (if (satisfies? IDeref ratom)
        (if (= path [])
          (reset! ratom new-value)
          (swap! ratom assoc-in path new-value))
        (ratom path new-value))
      new-value))

  ISwap
  (-swap! [a f]          (-reset! a (f (._peek a))))
  (-swap! [a f x]        (-reset! a (f (._peek a) x)))
  (-swap! [a f x y]      (-reset! a (f (._peek a) x y)))
  (-swap! [a f x y more] (-reset! a (apply f (._peek a) x y more)))

  IPrintWithWriter
  (-pr-writer [a w opts] (pr-atom a w opts "RCursor" {:val (-deref a)
                                                      :path path}))

  IWatchable
  (-notify-watches [this old new] (notify-w this old new))
  (-add-watch [this key f]        (add-w this key f))
  (-remove-watch [this key]       (remove-w this key))

  IHash
  (-hash [_] (hash [ratom path])))

(defn cursor
  [^clj src path]
  (assert (or (satisfies? IReactiveAtom src)
              (and (ifn? src)
                   (not (vector? src))))
          (str "src must be a reactive atom or a function, not "
               (pr-str src)
               " while attempting to get path: "
               (pr-str path)))
  (->RCursor src path nil nil nil))


;;; with-let support

(defn with-let-destroy [v]
  (when-some [f (.-destroy v)]
    (f)))

(defn with-let-values [key]
  (if-some [c *ratom-context*]
    (cached-reaction array c key nil with-let-destroy)
    (array)))


;;;; reaction

(defprotocol IDisposable
  (dispose! [this])
  (add-on-dispose! [this f]))

(defprotocol IRunnable
  (run [this]))

(defn- handle-reaction-change [^clj this sender old new]
  (._handle-change this sender old new))

;; Fields of a Reaction javascript object
;; - auto_run
;; - captured
;; - caught
;; - f
;; - ratomGeneration
;; - state
;; - watches
;; - watching
(deftype Reaction [f ^:mutable state ^:mutable ^boolean dirty? ^boolean nocache?
                   ^:mutable watching ^:mutable watches ^:mutable auto-run
                   ^:mutable caught]
  IAtom
  IReactiveAtom

  IWatchable
  (-notify-watches [this old new] (notify-w this old new))
  (-add-watch [this key f]        (add-w this key f))
  (-remove-watch [this key]
    (let [was-empty (empty? watches)]
      (remove-w this key)
      (when (and (not was-empty)
                 (empty? watches)
                 (nil? auto-run))
        (dispose! this))))

  IReset
  (-reset! [a newval]
    (assert (fn? (.-on-set a)) "Reaction is read only; on-set is not allowed")
    (let [oldval state]
      (set! state newval)
      (.on-set a oldval newval)
      (notify-w a oldval newval)
      newval))

  ISwap
  (-swap! [a f]          (-reset! a (f (._peek-at a))))
  (-swap! [a f x]        (-reset! a (f (._peek-at a) x)))
  (-swap! [a f x y]      (-reset! a (f (._peek-at a) x y)))
  (-swap! [a f x y more] (-reset! a (apply f (._peek-at a) x y more)))

  Object
  (_peek-at [this]
    (binding [*ratom-context* nil]
      (-deref this)))

  (_handle-change [this sender oldval newval]
    (when-not (or (identical? oldval newval)
                  dirty?)
      (if (nil? auto-run)
        (do
          (set! dirty? true)
          (rea-enqueue this))
        (if (true? auto-run)
          (._run this false)
          (auto-run this)))))

  (_update-watching [this derefed]
    (let [new (set derefed)
          old (set watching)]
      (set! watching derefed)
      (doseq [w (s/difference new old)]
        (-add-watch w this handle-reaction-change))
      (doseq [w (s/difference old new)]
        (-remove-watch w this))))

  (_queued-run [this]
    (when (and dirty? (some? watching))
      (._run this true)))

  (_try-capture [this f]
    (try
      (set! caught nil)
      (deref-capture f this)
      (catch :default e
        (set! state e)
        (set! caught e)
        (set! dirty? false))))

  (_run [this check]
    (let [oldstate state
          res (if check
                (._try-capture this f)
                (deref-capture f this))]
      (when-not nocache?
        (set! state res)
        ;; Use = to determine equality from reactions, since
        ;; they are likely to produce new data structures.
        (when-not (or (nil? watches)
                      (= oldstate res))
          (notify-w this oldstate res)))
      res))

  (_set-opts [this {:keys [auto-run on-set on-dispose no-cache]}]
    (when (some? auto-run)
      (set! (.-auto-run this) auto-run))
    (when (some? on-set)
      (set! (.-on-set this) on-set))
    (when (some? on-dispose)
      (set! (.-on-dispose this) on-dispose))
    (when (some? no-cache)
      (set! (.-nocache? this) no-cache)))

  IRunnable
  (run [this]
    (flush!)
    (._run this false))

  IDeref
  (-deref [this]
    (when-some [e caught]
      (throw e))
    (let [non-reactive (nil? *ratom-context*)]
      (when non-reactive
        (flush!))
      (if (and non-reactive (nil? auto-run))
        (when dirty?
          (let [oldstate state]
            (set! state (f))
            (when-not (or (nil? watches) (= oldstate state))
              (notify-w this oldstate state))))
        (do
          (notify-deref-watcher! this)
          (when dirty?
            (._run this false)))))
    state)

  IDisposable
  (dispose! [this]
    (let [s state
          wg watching]
      (set! watching nil)
      (set! state nil)
      (set! auto-run nil)
      (set! dirty? true)
      (doseq [w (set wg)]
        (-remove-watch w this))
      (when (some? (.-on-dispose this))
        (.on-dispose this s))
      (when-some [a (.-on-dispose-arr this)]
        (dotimes [i (alength a)]
          ((aget a i) this)))))

  (add-on-dispose! [this f]
    ;; f is called with the reaction as argument when it is no longer active
    (if-some [a (.-on-dispose-arr this)]
      (.push a f)
      (set! (.-on-dispose-arr this) (array f))))

  IEquiv
  (-equiv [o other] (identical? o other))

  IPrintWithWriter
  (-pr-writer [a w opts] (pr-atom a w opts "Reaction" {:val (-deref a)}))

  IHash
  (-hash [this] (goog/getUid this)))


(defn make-reaction [f & {:keys [auto-run on-set on-dispose]}]
  (let [reaction (->Reaction f nil true false nil nil nil nil)]
    (._set-opts reaction {:auto-run auto-run
                          :on-set on-set
                          :on-dispose on-dispose})
    reaction))



(def ^:private temp-reaction (make-reaction nil))


(defn run-in-reaction
  "Evaluates `f` and returns the result.  If `f` calls `deref` on any ratoms,
   creates a new Reaction that watches those atoms and calls `run` whenever
   any of those watched ratoms change.  Also, the new reaction is added to
   list of 'watches' of each of the ratoms. The `run` parameter is a function
   that should expect one argument.  It is passed `obj` when run.  The `opts`
   are any options accepted by a Reaction and will be set on the newly created
   Reaction. Sets the newly created Reaction to the `key` on `obj`."
  [f obj key run opts]
  (let [r temp-reaction
        res (deref-capture f r)]
    (when-not (nil? (.-watching r))
      (set! temp-reaction (make-reaction nil))
      (._set-opts r opts)
      (set! (.-f r) f)
      (set! (.-auto-run r) #(run obj))
      (obj/set obj key r))
    res))

(defn check-derefs [f]
  (let [ctx (js-obj)
        res (in-context ctx f)]
    [res (some? (.-captured ctx))]))


;;; wrap

(deftype Wrapper [^:mutable state callback ^:mutable ^boolean changed
                  ^:mutable watches]

  IAtom

  IDeref
  (-deref [this]
    (when (dev?)
      (when (and changed (some? *ratom-context*))
        (warn "derefing stale wrap: "
              (pr-str this))))
    state)

  IReset
  (-reset! [this newval]
    (let [oldval state]
      (set! changed true)
      (set! state newval)
      (when (some? watches)
        (notify-w this oldval newval))
      (callback newval)
      newval))

  ISwap
  (-swap! [a f]          (-reset! a (f state)))
  (-swap! [a f x]        (-reset! a (f state x)))
  (-swap! [a f x y]      (-reset! a (f state x y)))
  (-swap! [a f x y more] (-reset! a (apply f state x y more)))

  IEquiv
  (-equiv [_ ^clj other]
          (and (instance? Wrapper other)
               ;; If either of the wrappers have changed, equality
               ;; cannot be relied on.
               (not changed)
               (not (.-changed other))
               (= state (.-state other))
               (= callback (.-callback other))))

  IWatchable
  (-notify-watches [this old new] (notify-w this old new))
  (-add-watch [this key f]        (add-w this key f))
  (-remove-watch [this key]       (remove-w this key))

  IPrintWithWriter
  (-pr-writer [a w opts] (pr-atom a w opts "Wrapper" {:val (-deref a)})))

(defn make-wrapper [value callback-fn args]
  (->Wrapper value
             (util/make-partial-fn callback-fn args)
             false nil))




#_(do
  (defn ratom-perf []
    (set! debug false)
    (dotimes [_ 10]
      (let [nite 100000
            a (atom 0)
            f (fn []
                (quot @a 10))
            mid (make-reaction f)
            res (track! (fn []
                          ;; (with-let [x 1])
                          ;; @(track f)
                          (inc @mid)
                          ))]
        @res
        (time (dotimes [x nite]
                (swap! a inc)
                (flush!)))
        (dispose! res))))
  (ratom-perf))
