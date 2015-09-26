(ns reagent.ratom
  (:refer-clojure :exclude [atom])
  (:require-macros [reagent.ratom :refer [with-let]])
  (:require [reagent.impl.util :as util]
            [reagent.debug :refer-macros [dbg log warn error dev?]]))

(declare ^:dynamic *ratom-context*)
(defonce cached-reactions {})

(defn ^boolean reactive? []
  (not (nil? *ratom-context*)))

(defonce ^boolean debug false)
(defonce ^boolean silent false)
(defonce generation 0)

(defonce -running (clojure.core/atom 0))

(defn running []
  (+ @-running
     (count cached-reactions)))

(defn capture-derefed [f obj]
  (set! (.-cljsCaptured obj) nil)
  (when (dev?)
    (set! (.-ratomGeneration obj)
          (set! generation (inc generation))))
  (binding [*ratom-context* obj]
    (f)))

(defn captured [obj]
  (when-not (nil? (.-cljsCaptured obj))
    obj))

(defn- -captured [obj]
  (let [c (.-cljsCaptured obj)]
    (set! (.-cljsCaptured obj) nil)
    c))

(defn- notify-deref-watcher! [derefable]
  (let [obj *ratom-context*]
    (when-not (nil? obj)
      (let [captured (.-cljsCaptured obj)]
        (set! (.-cljsCaptured obj)
              (conj (if (nil? captured) #{} captured)
                    derefable))))))

(def reaction-counter 0)

(defn reaction-key [r]
  (if-some [k (.-reaction-id r)]
    k
    (->> reaction-counter inc
         (set! reaction-counter)
         (set! (.-reaction-id r)))))

(defn- check-watches [old new]
  (when debug
    (swap! -running + (- (count new) (count old))))
  new)

(defn- add-w [this key f]
  (let [w (.-watches this)]
    (set! (.-watches this) (check-watches w (assoc w key f)))))

(defn- remove-w [this key]
  (let [w (.-watches this)]
    (set! (.-watches this) (check-watches w (dissoc w key)))))

(defn- pr-atom [a writer opts s]
  (-write writer (str "#<" s " "))
  (pr-writer (binding [*ratom-context* nil] (-deref a)) writer opts)
  (-write writer ">"))


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
        (-notify-watches a old-value new-value))
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
  (-notify-watches [this old new]
    (reduce-kv (fn [_ k f] (f k this old new) nil)
               nil watches))
  (-add-watch [this key f]  (add-w this key f))
  (-remove-watch [this key] (remove-w this key))

  IHash
  (-hash [this] (goog/getUid this)))

(defn atom
  "Like clojure.core/atom, except that it keeps track of derefs."
  ([x] (RAtom. x nil nil nil))
  ([x & {:keys [meta validator]}] (RAtom. x meta validator nil)))



;;; track

(declare make-reaction)

(defn- cached-reaction [f key obj destroy]
  (if-some [r (get cached-reactions key)]
    (-deref r)
    (if-not (nil? *ratom-context*)
      (let [r (make-reaction
               f :on-dispose (fn [x]
                               (set! cached-reactions
                                     (dissoc cached-reactions key))
                               (when-not (nil? obj)
                                 (set! (.-reaction obj) nil))
                               (when-not (nil? destroy)
                                 (destroy x))
                               nil))
            v (-deref r)]
        (set! cached-reactions (assoc cached-reactions key r))
        (when-not (nil? obj)
          (set! (.-reaction obj) r))
        v)
      (f))))

(deftype Track [f key ^:mutable reaction]
  IReactiveAtom

  IDeref
  (-deref [this]
    (if-some [r reaction]
      (-deref r)
      (cached-reaction f key this nil)))

  IEquiv
  (-equiv [o other]
    (and (instance? Track other)
         (= key (.-key other))))

  IHash
  (-hash [_] (hash key))

  IPrintWithWriter
  (-pr-writer [a w opts] (pr-atom a w opts "Track:")))

(defn make-track [f args]
  (Track. #(apply f args) [f args] nil))

(defn make-track! [f args]
  (let [r (make-reaction #(-deref (make-track f args))
                         :auto-run :async)]
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
  (-equiv [o other]
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
        (-notify-watches this oldstate newstate))))

  IDeref
  (-deref [this]
    (let [oldstate state
          newstate (if-some [r reaction]
                     (-deref r)
                     (let [f (if (satisfies? IDeref ratom)
                               #(get-in @ratom path)
                               #(ratom path))]
                       (cached-reaction f [::cursor ratom path] this nil)))]
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
  (-notify-watches [this old new]
    (reduce-kv (fn [_ k f] (f k this old new) nil)
               nil watches))
  (-add-watch [this key f]  (add-w this key f))
  (-remove-watch [this key] (remove-w this key))

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
  (when (< 1 (alength v))
    ((aget v 1))))

(defn with-let-value [key]
  (if-some [c *ratom-context*]
    (cached-reaction array [(reaction-key c) key]
                     nil with-let-destroy)
    (array)))


;;;; reaction

(defprotocol IDisposable
  (dispose! [this]))

(defprotocol IRunnable
  (run [this]))

(defprotocol IComputedImpl
  (-peek-at [this])
  (^boolean -check-clean [this])
  (-handle-change [this sender oldval newval])
  (-update-watching [this derefed]))

(def ^:const clean 0)
(def ^:const maybe-dirty 1)
(def ^:const dirty 2)

(deftype Reaction [f ^:mutable state ^:mutable ^number dirtyness
                   ^:mutable watching ^:mutable watches
                   ^:mutable auto-run on-set on-dispose ^boolean nocache?]
  IAtom
  IReactiveAtom

  IWatchable
  (-notify-watches [this old new]
    (reduce-kv (fn [_ k f] (f k this old new) nil)
               nil watches))
  (-add-watch [this key f] (add-w this key f))
  (-remove-watch [this key]
    (remove-w this key)
    (when (and (empty? watches)
               (nil? auto-run))
      (dispose! this)))

  IReset
  (-reset! [a newval]
    (assert (ifn? on-set) "Reaction is read only.")
    (let [oldval state]
      (set! state newval)
      (on-set oldval newval)
      (-notify-watches a oldval newval)
      newval))

  ISwap
  (-swap! [a f]          (-reset! a (f (-peek-at a))))
  (-swap! [a f x]        (-reset! a (f (-peek-at a) x)))
  (-swap! [a f x y]      (-reset! a (f (-peek-at a) x y)))
  (-swap! [a f x y more] (-reset! a (apply f (-peek-at a) x y more)))

  IComputedImpl
  (-peek-at [this]
    (if (== dirtyness clean)
      state
      (binding [*ratom-context* nil]
        (-deref this))))

  (-check-clean [this]
    (when (== dirtyness maybe-dirty)
      (let [ar auto-run]
        (set! auto-run nil)
        (doseq [w watching :while (== dirtyness maybe-dirty)]
          (when (and (instance? Reaction w)
                     (not (-check-clean w)))
            (._try-run w this)))
        (set! auto-run ar))
      (when (== dirtyness maybe-dirty)
        (set! dirtyness clean)))
    (== dirtyness clean))

  (-handle-change [this sender oldval newval]
    (let [old-dirty dirtyness
          new-dirty (if (identical? oldval newval)
                      (if (instance? Reaction sender)
                        maybe-dirty clean)
                      dirty)]
      (when (> new-dirty old-dirty)
        (set! dirtyness new-dirty)
        (when (== old-dirty clean)
          (if-some [ar auto-run]
            (when-not (and (identical? ar run)
                           (-check-clean this))
              (ar this))
            (-notify-watches this state state)))))
    nil)

  (-update-watching [this derefed]
    (doseq [w derefed]
      (when-not (contains? watching w)
        (-add-watch w this -handle-change)))
    (doseq [w watching]
      (when-not (contains? derefed w)
        (-remove-watch w this)))
    (set! watching derefed)
    nil)

  Object
  (_try-run [this other]
    (try
      (if-some [ar auto-run]
        (ar this)
        (run this))
      (catch :default e
        ;; Just log error: it will most likely pop up again at deref time.
        (when-not silent (error "Error in reaction:" e))
        (set! dirtyness dirty)
        (set! (.-dirtyness other) dirty)))
    nil)

  IRunnable
  (run [this]
    (let [oldstate state
          res (capture-derefed f this)
          derefed (-captured this)]
      (when (not= derefed watching)
        (-update-watching this derefed))
      (set! dirtyness clean)
      (when-not nocache?
        (set! state res)
        ;; Use = to determine equality from reactions, since
        ;; they are likely to produce new data structures.
        (when-not (or (nil? watches)
                      (= oldstate res))
          (-notify-watches this oldstate res)))
      res))

  IDeref
  (-deref [this]
    (-check-clean this)
    (if (and (nil? *ratom-context*)
             (nil? auto-run))
      (do
        (when-not (== dirtyness clean)
          (let [oldstate state
                newstate (f)]
            (set! state newstate)
            (when-not (or (nil? watches)
                          (= oldstate newstate))
              (-notify-watches this oldstate newstate)))))
      (do
        (notify-deref-watcher! this)
        (when-not (== dirtyness clean)
          (run this))))
    state)

  IDisposable
  (dispose! [this]
    (doseq [w watching]
      (remove-watch w this))
    (let [s state]
      (set! watching nil)
      (set! state nil)
      (set! auto-run nil)
      (set! dirtyness dirty)
      (when-not (nil? on-dispose)
        (on-dispose s)))
    nil)

  IEquiv
  (-equiv [o other] (identical? o other))

  IPrintWithWriter
  (-pr-writer [a w opts] (pr-atom a w opts (str "Reaction " (hash a) ":")))

  IHash
  (-hash [this] (reaction-key this)))


;;; Queueing

;; Gets set up from batching
;; TODO: Refactor so that isn't needed
(defonce render-queue nil)

(def dirty-queue nil)

(defn enqueue [r]
  (when (nil? dirty-queue)
    (set! dirty-queue (array))
    (.schedule render-queue))
  (.push dirty-queue r))

(defn flush! []
  (let [q dirty-queue]
    (when-not (nil? q)
      (set! dirty-queue nil)
      (dotimes [i (alength q)]
        (let [r (aget q i)]
          (when-not (or (nil? (.-auto-run r))
                        (-check-clean r))
            (run r)))))))


(defn make-reaction [f & {:keys [auto-run on-set on-dispose derefed no-cache
                                 capture]}]
  (let [runner (case auto-run
                 true run
                 :async enqueue
                 auto-run)
        derefs (if-some [c capture]
                 (-captured c)
                 derefed)
        dirty (if (nil? derefs) dirty clean)
        nocache (if (nil? no-cache) false no-cache)
        reaction (Reaction. f nil dirty nil nil
                            runner on-set on-dispose nocache)]
    (when-some [rid (some-> capture .-reaction-id)]
      (set! (.-reaction-id reaction) rid))
    (when-not (nil? derefed)
      (warn "using derefed is deprecated"))
    (when-not (nil? derefs)
      (when (dev?)
        (set! (.-ratomGeneration reaction)
              (.-ratomGeneration derefs)))
      (-update-watching reaction derefs))
    reaction))



;;; wrap

(deftype Wrapper [^:mutable state callback ^:mutable changed
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
        (-notify-watches this oldval newval))
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
  (-notify-watches [this old new]
    (reduce-kv (fn [_ k f] (f k this old new) nil)
               nil watches))
  (-add-watch [this key f]  (add-w this key f))
  (-remove-watch [this key] (remove-w this key))

  IPrintWithWriter
  (-pr-writer [a w opts] (pr-atom a w opts "Wrap:")))

(defn make-wrapper [value callback-fn args]
  (Wrapper. value
            (util/partial-ifn. callback-fn args nil)
            false nil))

(comment
  (defn ratom-perf []
    (dbg "ratom-perf")
    (set! debug false)
    (dotimes [_ 10]
      (let [nite 100000
            a (atom 0)
            f (fn []
                ;; (with-let [x 1])
                (quot @a 10))
            mid (make-reaction f)
            res (make-reaction (fn []
                                 ;; @(track f)
                                 (inc @mid))
                               :auto-run true)]
        @res
        (time (dotimes [x nite]
                (swap! a inc)))
        (dispose! res))))
  (enable-console-print!)
  (ratom-perf))
