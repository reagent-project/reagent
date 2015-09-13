(ns reagent.ratom
  (:refer-clojure :exclude [atom])
  (:require-macros [reagent.ratom])
  (:require [reagent.impl.util :as util]
            [reagent.debug :refer-macros [dbg log warn dev?]]))

(declare ^:dynamic *ratom-context*)

(defonce ^boolean debug false)
(defonce ^boolean silent false)

(defonce -running (clojure.core/atom 0))

(defn running [] @-running)

(defn capture-derefed [f obj]
  (set! (.-cljsCaptured obj) nil)
  (binding [*ratom-context* obj]
    (f)))

(defn captured [obj]
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

(defn- check-watches [old new]
  (when debug
    (swap! -running + (- (count new) (count old))))
  new)


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
  (-swap! [a f]
    (-reset! a (f state)))
  (-swap! [a f x]
    (-reset! a (f state x)))
  (-swap! [a f x y]
    (-reset! a (f state x y)))
  (-swap! [a f x y more]
    (-reset! a (apply f state x y more)))

  IMeta
  (-meta [_] meta)

  IPrintWithWriter
  (-pr-writer [a writer opts]
    (-write writer "#<Atom: ")
    (pr-writer state writer opts)
    (-write writer ">"))

  IWatchable
  (-notify-watches [this oldval newval]
    (reduce-kv (fn [_ key f]
                 (f key this oldval newval)
                 nil)
               nil watches))
  (-add-watch [this key f]
    (set! watches (check-watches watches (assoc watches key f))))
  (-remove-watch [this key]
    (set! watches (check-watches watches (dissoc watches key))))

  IHash
  (-hash [this] (goog/getUid this)))

(defn atom
  "Like clojure.core/atom, except that it keeps track of derefs."
  ([x] (RAtom. x nil nil nil))
  ([x & {:keys [meta validator]}] (RAtom. x meta validator nil)))



;;; monitor

(declare make-reaction)

(defonce cached-reactions {})

(defn- cached-reaction [f key obj]
  (if-some [r (get cached-reactions key)]
    (-deref r)
    (if (some? *ratom-context*)
      (let [r (make-reaction
               f :on-dispose (fn []
                               (set! cached-reactions
                                     (dissoc cached-reactions key))
                               (set! (.-reaction obj) nil)))
            v (-deref r)]
        (set! cached-reactions (assoc cached-reactions key r))
        (set! (.-reaction obj) r)
        v)
      (f))))

(deftype Monitor [f key ^:mutable reaction]
  IReactiveAtom

  IDeref
  (-deref [this]
    (if-some [r reaction]
      (-deref r)
      (cached-reaction f key this)))

  IEquiv
  (-equiv [o other]
    (and (instance? Monitor other)
         (= key (.-key other))))

  IHash
  (-hash [this] (hash key))

  IPrintWithWriter
  (-pr-writer [a writer opts]
    (-write writer (str "#<Monitor: " key " "))
    (binding [*ratom-context* nil]
      (pr-writer (-deref a) writer opts))
    (-write writer ">")))

(defn monitor [f & args]
  (Monitor. #(apply f args) [f args] nil))

;;; cursor

(deftype RCursor [ratom path ^:mutable reaction]
  IAtom
  IReactiveAtom

  IEquiv
  (-equiv [o other]
    (and (instance? RCursor other)
         (= path (.-path other))
         (= ratom (.-ratom other))))

  Object
  (_reaction [this]
    (if (nil? reaction)
      (set! reaction
            (if (satisfies? IDeref ratom)
              (make-reaction #(get-in @ratom path)
                             :on-set (if (= path [])
                                       #(reset! ratom %2)
                                       #(swap! ratom assoc-in path %2)))
              (make-reaction #(ratom path)
                             :on-set #(ratom path %2))))
      reaction))

  (_peek [this]
    (binding [*ratom-context* nil]
      (-deref (._reaction this))))

  IDeref
  (-deref [this]
    (-deref (._reaction this)))

  IReset
  (-reset! [this new-value]
    (-reset! (._reaction this) new-value))

  ISwap
  (-swap! [a f]
    (-swap! (._reaction a) f))
  (-swap! [a f x]
    (-swap! (._reaction a) f x))
  (-swap! [a f x y]
    (-swap! (._reaction a) f x y))
  (-swap! [a f x y more]
    (-swap! (._reaction a) f x y more))

  IPrintWithWriter
  (-pr-writer [a writer opts]
    (-write writer (str "#<Cursor: " path " "))
    (pr-writer (._peek a) writer opts)
    (-write writer ">"))

  IWatchable
  (-notify-watches [this oldval newval]
    (-notify-watches (._reaction this) oldval newval))
  (-add-watch [this key f]
    (-add-watch (._reaction this) key f))
  (-remove-watch [this key]
    (-remove-watch (._reaction this) key))

  IHash
  (-hash [this] (hash [ratom path])))

(defn cursor
  [src path]
  (if (satisfies? IDeref path)
    (do
      (warn "Calling cursor with an atom as the second arg is "
            "deprecated, in (cursor "
            src " " (pr-str path) ")")
      (assert (satisfies? IReactiveAtom path)
              (str "src must be a reactive atom, not "
                   (pr-str path)))
      (RCursor. path src nil))
    (do
      (assert (or (satisfies? IReactiveAtom src)
                  (and (ifn? src)
                       (not (vector? src))))
              (str "src must be a reactive atom or a function, not "
                   (pr-str src)))
      (RCursor. src path nil))))



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
  (-notify-watches [this oldval newval]
    (reduce-kv (fn [_ key f]
                 (f key this oldval newval)
                 nil)
               nil watches)
    nil)

  (-add-watch [_ key f]
    (set! watches (check-watches watches (assoc watches key f))))

  (-remove-watch [this key]
    (set! watches (check-watches watches (dissoc watches key)))
    (when (and (empty? watches)
               (nil? auto-run))
      (dispose! this)))

  IReset
  (-reset! [a newval]
    (let [oldval state]
      (set! state newval)
      (when (some? on-set)
        (set! dirtyness dirty)
        (on-set oldval newval))
      (-notify-watches a oldval newval)
      newval))

  ISwap
  (-swap! [a f]
    (-reset! a (f (-peek-at a))))
  (-swap! [a f x]
    (-reset! a (f (-peek-at a) x)))
  (-swap! [a f x y]
    (-reset! a (f (-peek-at a) x y)))
  (-swap! [a f x y more]
    (-reset! a (apply f (-peek-at a) x y more)))

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
        (doseq [w watching]
          (when (and (instance? Reaction w)
                     (not (-check-clean w)))
            (._try-run this w)))
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
  (_try-run [_ parent]
    (try
      (if-some [ar (.-auto-run parent)]
        (ar parent)
        (run parent))
      (catch :default e
        ;; Just log error: it will most likely pop up again at deref time.
        (when-not silent
          (js/console.error "Error in reaction:" e))
        (set! (.-dirtyness parent) dirty)
        (set! dirtyness dirty))))

  IRunnable
  (run [this]
    (let [oldstate state
          res (capture-derefed f this)
          derefed (captured this)]
      (when (not= derefed watching)
        (-update-watching this derefed))
      (set! dirtyness clean)
      (when-not nocache?
        (set! state res)
        ;; Use = to determine equality from reactions, since
        ;; they are likely to produce new data structures.
        (when (and (some? watches)
                   (not= oldstate res))
          (-notify-watches this oldstate res)))
      res))

  IDeref
  (-deref [this]
    (-check-clean this)
    (if (and (nil? auto-run) (nil? *ratom-context*))
      (when-not (== dirtyness clean)
        (let [oldstate state
              newstate (f)]
          (set! state newstate)
          (when (and (some? watches)
                     (not= oldstate newstate))
            (-notify-watches this oldstate newstate))))
      (do
        (notify-deref-watcher! this)
        (when-not (== dirtyness clean)
          (run this))))
    state)

  IDisposable
  (dispose! [this]
    (doseq [w watching]
      (remove-watch w this))
    (set! watching nil)
    (set! state nil)
    (set! auto-run nil)
    (set! dirtyness dirty)
    (when (some? on-dispose)
      (on-dispose))
    nil)

  IEquiv
  (-equiv [o other] (identical? o other))

  IPrintWithWriter
  (-pr-writer [this writer opts]
    (-write writer (str "#<Reaction " (hash this) ": "))
    (pr-writer state writer opts)
    (-write writer ">"))

  IHash
  (-hash [this] (goog/getUid this)))



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
    (when (some? q)
      (set! dirty-queue nil)
      (dotimes [i (alength q)]
        (let [r (aget q i)]
          (when-not (or (nil? (.-auto-run r))
                        (-check-clean r))
            (run r)))))))


(defn make-reaction [f & {:keys [auto-run on-set on-dispose derefed no-cache]}]
  (let [runner (case auto-run
                 true run
                 :async enqueue
                 auto-run)
        dirty (if (nil? derefed) dirty clean)
        nocache (if (nil? no-cache) false no-cache)
        reaction (Reaction. f nil dirty nil nil
                            runner on-set on-dispose nocache)]
    (when-not (nil? derefed)
      (-update-watching reaction derefed))
    (when (keyword-identical? auto-run :async)
      (enqueue reaction))
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
  (-swap! [a f]
    (-reset! a (f state)))
  (-swap! [a f x]
    (-reset! a (f state x)))
  (-swap! [a f x y]
    (-reset! a (f state x y)))
  (-swap! [a f x y more]
    (-reset! a (apply f state x y more)))

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
  (-notify-watches [this oldval newval]
    (reduce-kv (fn [_ key f]
                 (f key this oldval newval)
                 nil)
               nil watches))
  (-add-watch [this key f]
    (set! watches (assoc watches key f)))
  (-remove-watch [this key]
    (set! watches (dissoc watches key)))

  IPrintWithWriter
  (-pr-writer [_ writer opts]
    (-write writer "#<wrap: ")
    (pr-writer state writer opts)
    (-write writer ">")))

(defn make-wrapper [value callback-fn args]
  (Wrapper. value
            (util/partial-ifn. callback-fn args nil)
            false nil))

(comment
  (def perf-check 0)
  (defn ratom-perf []
    (dbg "ratom-perf")
    (set! debug false)
    (dotimes [_ 10]
      (set! perf-check 0)
      (let [nite 100000
            a (atom 0)
            mid (make-reaction (fn [] (inc @a)))
            res (make-reaction (fn []
                                 (set! perf-check (inc perf-check))
                                 (inc @mid))
                               :auto-run true)]
        @res
        (time (dotimes [x nite]
                (swap! a inc)))
        (dispose! res)
        (assert (= perf-check (inc nite))))))
  (enable-console-print!)
  (ratom-perf))
