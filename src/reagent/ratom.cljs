(ns reagent.ratom
  (:refer-clojure :exclude [atom])
  (:require-macros [reagent.ratom :refer [with-let]])
  (:require [reagent.impl.util :as util]
            [reagent.debug :refer-macros [dbg log warn error dev?]]))

(declare ^:dynamic *ratom-context*)
(defonce ^boolean debug false)
(defonce ^boolean silent false)
(defonce ^:private generation 0)
(defonce ^:private -running (clojure.core/atom 0))

(defn ^boolean reactive? []
  (not (nil? *ratom-context*)))


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

(defn- ^boolean in-arr [a x]
  (not (or (nil? a)
           (== -1 (.indexOf a x)))))

(defn- in-context [obj f]
  (binding [*ratom-context* obj]
    (f)))

(defn- deref-capture [f r]
  (set! (.-captured r) nil)
  (when (dev?)
    (set! (.-ratomGeneration r)
          (set! generation (inc generation))))
  (let [res (in-context r f)
        c (.-captured r)]
    (set! (.-dirty? r) false)
    (when-not (arr-eq c (.-watching r))
      (._update-watching r c))
    res))

(defn- notify-deref-watcher! [derefable]
  (when-some [r *ratom-context*]
    (let [c (.-captured r)]
      (if (nil? c)
        (set! (.-captured r) (array derefable))
        (when-not (in-arr c derefable)
          (.push c derefable))))))

(defn- check-watches [old new]
  (when debug
    (swap! -running + (- (count new) (count old))))
  new)

(defn- add-w [this key f]
  (let [w (.-watches this)]
    (set! (.-watches this) (check-watches w (assoc w key f)))
    (set! (.-watchesArr this) nil)))

(defn- remove-w [this key]
  (let [w (.-watches this)]
    (set! (.-watches this) (check-watches w (dissoc w key)))
    (set! (.-watchesArr this) nil)))

(defn- notify-w [this old new]
  (let [w (.-watchesArr this)
        a (if (nil? w)
            (set! (.-watchesArr this) (array))
            w)]
    (when (nil? w)
      ;; Copy watches to an array for speed
      (reduce-kv #(.push a %2 %3)
                 nil (.-watches this)))
    (let [len (alength a)]
      (loop [i 0]
        (when (< i len)
          (let [k (aget a i)
                f (aget a (inc i))]
            (f k this old new))
          (recur (+ 2 i)))))))

(defn- pr-atom [a writer opts s]
  (-write writer (str "#<" s " "))
  (pr-writer (binding [*ratom-context* nil] (-deref a)) writer opts)
  (-write writer ">"))


;;; Queueing

(defonce ^:private rea-queue nil)
(def ^:private empty-context #js{})

(defn- rea-enqueue [r]
  (when (nil? rea-queue)
    (set! rea-queue (array))
    ;; Get around ugly circular dependency. TODO: Fix.
    (js/reagent.impl.batching.schedule))
  (.push rea-queue r))

(defn- run-queue [q]
  (dotimes [i (alength q)]
      (let [r (aget q i)]
        (._try-run r))))

(defn flush! []
  (when-some [q rea-queue]
    (set! rea-queue nil)
    (binding [*ratom-context* empty-context]
      (run-queue q))))


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

  IMeta
  (-meta [_] meta)

  IPrintWithWriter
  (-pr-writer [a w opts] (pr-atom a w opts "Atom:"))

  IWatchable
  (-notify-watches [this old new] (notify-w this old new))
  (-add-watch [this key f]        (add-w this key f))
  (-remove-watch [this key]       (remove-w this key))

  IHash
  (-hash [this] (goog/getUid this)))

(defn atom
  "Like clojure.core/atom, except that it keeps track of derefs."
  ([x] (RAtom. x nil nil nil))
  ([x & {:keys [meta validator]}] (RAtom. x meta validator nil)))



;;; track

(declare make-reaction)

(def ^{:private true :const true} cache-key "reagReactionCache")

(defn- cached-reaction [f o k obj destroy]
  (let [m (aget o cache-key)
        m (if (nil? m) {} m)
        r (get m k)]
    (if-not (nil? r)
      (-deref r)
      (if (nil? *ratom-context*)
        (f)
        (let [r (make-reaction
                 f :on-dispose (fn [x]
                                 (when debug (swap! -running dec))
                                 (as-> (aget o cache-key) _
                                   (dissoc _ k)
                                   (aset o cache-key _))
                                 (when-not (nil? obj)
                                   (set! (.-reaction obj) nil))
                                 (when-not (nil? destroy)
                                   (destroy x))
                                 nil))
              v (-deref r)]
          (aset o cache-key (assoc m k r))
          (when debug (swap! -running inc))
          (when-not (nil? obj)
            (set! (.-reaction obj) r))
          v)))))

(deftype Track [f args ^:mutable reaction]
  IReactiveAtom

  IDeref
  (-deref [this]
    (if-some [r reaction]
      (-deref r)
      (cached-reaction #(apply f args) f args this nil)))

  IEquiv
  (-equiv [_ other]
    (and (instance? Track other)
         (= key (.-key other))))

  IHash
  (-hash [_] (hash key))

  IPrintWithWriter
  (-pr-writer [a w opts] (pr-atom a w opts "Track:")))

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
  (-equiv [_ other]
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
      (when-not (nil? watches)
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
  (-pr-writer [a w opts] (pr-atom a w opts (str "Cursor: " path)))

  IWatchable
  (-notify-watches [this old new] (notify-w this old new))
  (-add-watch [this key f]        (add-w this key f))
  (-remove-watch [this key]       (remove-w this key))

  IHash
  (-hash [_] (hash [ratom path])))

(defn cursor
  [src path]
  (assert (or (satisfies? IReactiveAtom src)
              (and (ifn? src)
                   (not (vector? src))))
          (str "src must be a reactive atom or a function, not "
               (pr-str src)))
  (RCursor. src path nil nil nil))



;;; with-let support

(defn with-let-destroy [v]
  (when-some [f (.-destroy v)]
    (f)))

(defn with-let-values [key]
  (if-some [c *ratom-context*]
    (cached-reaction array c key
                     nil with-let-destroy)
    (array)))


;;;; reaction

(defprotocol IDisposable
  (dispose! [this]))

(defprotocol IRunnable
  (run [this]))

(defn- handle-reaction-change [this sender old new]
  (._handle-change this sender old new))


(deftype Reaction [f ^:mutable state ^:mutable ^boolean dirty? ^boolean nocache?
                   ^:mutable watching ^:mutable watches ^:mutable auto-run]
  IAtom
  IReactiveAtom

  IWatchable
  (-notify-watches [this old new] (notify-w this old new))
  (-add-watch [this key f]        (add-w this key f))
  (-remove-watch [this key]
    (remove-w this key)
    (when (and (empty? watches)
               (nil? auto-run))
      (dispose! this)))

  IReset
  (-reset! [a newval]
    (assert (fn? (.-on-set a)) "Reaction is read only.")
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
    (when-not (identical? oldval newval)
      (if-not (nil? *ratom-context*)
        (if-not (nil? auto-run)
          (auto-run this)
          (when-not dirty?
            (set! dirty? true)
            (._run this)))
        (do
          (set! dirty? true)
          (rea-enqueue this))))
    nil)

  (_update-watching [this derefed]
    (let [der (if (zero? (arr-len derefed)) nil derefed)
          wg watching]
      (set! watching der)
      (doseq [w der]
        (when-not (in-arr wg w) 
          (-add-watch w this handle-reaction-change)))
      (doseq [w wg]
        (when-not (in-arr der w)
          (-remove-watch w this))))
    nil)

  (_try-run [this other]
    (if-not (nil? auto-run)
      (auto-run this)
      (when (and dirty? (not (nil? watching)))
        (try
          (._run this)
          (catch :default e
            ;; Just log error: it will most likely pop up again at deref time.
            (when-not silent (error "Error in reaction:" e))
            (set! state nil)
            (notify-w this e nil)))))
    nil)

  (_run [this]
    (let [oldstate state
          res (deref-capture f this)]
      (when-not nocache?
        (set! state res)
        ;; Use = to determine equality from reactions, since
        ;; they are likely to produce new data structures.
        (when-not (or (nil? watches)
                      (= oldstate res))
          (notify-w this oldstate res)))
      res))

  (_set-opts [this {:keys [auto-run on-set on-dispose no-cache]}]
    (when-not (nil? auto-run)
      (set! (.-auto-run this) (case auto-run
                                true run
                                auto-run)))
    (when-not (nil? on-set)
      (set! (.-on-set this) on-set))
    (when-not (nil? on-dispose)
      (set! (.-on-dispose this) on-dispose))
    (when-not (nil? no-cache)
      (set! (.-nocache? this) no-cache)))

  IRunnable
  (run [this]
    (flush!)
    (._run this))

  IDeref
  (-deref [this]
    (when (nil? *ratom-context*)
      (flush!))
    (if-not (and (nil? auto-run) (nil? *ratom-context*))
      (do
        (notify-deref-watcher! this)
        (when dirty?
          (._run this)))
      (do
        (when dirty?
          (let [oldstate state]
            (set! state (f))
            (when-not (or (nil? watches)
                          (= oldstate state))
              (notify-w this oldstate state))))))
    state)

  IDisposable
  (dispose! [this]
    (let [s state
          wg watching]
      (set! watching nil)
      (set! state nil)
      (set! auto-run nil)
      (set! dirty? true)
      (doseq [w wg]
        (remove-watch w this))
      (when-not (nil? (.-on-dispose this))
        (.on-dispose this s)))
    nil)

  IEquiv
  (-equiv [o other] (identical? o other))

  IPrintWithWriter
  (-pr-writer [a w opts] (pr-atom a w opts (str "Reaction " (hash a) ":")))

  IHash
  (-hash [this] (goog/getUid this)))


(defn make-reaction [f & {:keys [auto-run on-set on-dispose]}]
  (let [reaction (Reaction. f nil true false nil nil nil)]
    (._set-opts reaction {:auto-run auto-run
                          :on-set on-set
                          :on-dispose on-dispose})
    reaction))


(def temp-reaction (make-reaction nil))

(defn run-in-reaction [f obj key run opts]
  (let [rea temp-reaction
        res (deref-capture f rea)]
    (when-not (nil? (.-watching rea))
      (set! temp-reaction (make-reaction nil))
      (set! (.-f rea) f)
      (._set-opts rea opts)
      (set! (.-auto-run rea) #(run obj))
      (aset obj key rea))
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
      (when-not (nil? watches)
        (notify-w this oldval newval))
      (callback newval)
      newval))

  ISwap
  (-swap! [a f]          (-reset! a (f state)))
  (-swap! [a f x]        (-reset! a (f state x)))
  (-swap! [a f x y]      (-reset! a (f state x y)))
  (-swap! [a f x y more] (-reset! a (apply f state x y more)))

  IEquiv
  (-equiv [_ other]
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
  (-pr-writer [a w opts] (pr-atom a w opts "Wrap:")))

(defn make-wrapper [value callback-fn args]
  (Wrapper. value
            (util/partial-ifn. callback-fn args nil)
            false nil))
