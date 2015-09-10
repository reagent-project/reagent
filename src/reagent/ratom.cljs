(ns reagent.ratom
  (:refer-clojure :exclude [atom])
  (:require-macros [reagent.ratom])
  (:require [reagent.impl.util :as util]
            [reagent.debug :refer-macros [dbg log warn dev?]]))

(declare ^:dynamic *ratom-context*)

(defonce ^boolean debug false)

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



;;; cursor

(declare make-reaction)

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
  (-check-clean [this])
  (-handle-change [this sender oldval newval])
  (-update-watching [this derefed]))

(def clean 0)
(def maybe-dirty 1)
(def dirty 2)

(deftype Reaction [f ^:mutable state ^:mutable ^number dirtyness
                   ^:mutable watching ^:mutable watches
                   auto-run on-set on-dispose ^:mutable ^boolean norun?]
  IAtom
  IReactiveAtom

  IWatchable
  (-notify-watches [this oldval newval]
    (reduce-kv (fn [_ key f]
                 (f key this oldval newval)
                 nil)
               nil watches))

  (-add-watch [this key f]
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
      (when on-set
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
      (set! norun? true)
      (doseq [w watching]
        (when (and (instance? Reaction w)
                   (not (== (.-dirtyness w) clean)))
          (-check-clean w)
          (when-not (== (.-dirtyness w) clean)
            (run w))))
      (set! norun? false)
      (when (== dirtyness maybe-dirty)
        (set! dirtyness clean))))

  (-handle-change [this sender oldval newval]
    (let [old-dirty dirtyness
          new-dirty (if (identical? oldval newval)
                      (if (instance? Reaction sender)
                        maybe-dirty clean)
                      dirty)]
      (when (> new-dirty old-dirty)
        (set! dirtyness new-dirty)
        (if (some? auto-run)
          (when-not norun?
            (-check-clean this)
            (when-not (== dirtyness clean)
              (auto-run this)))
          (-notify-watches this state state)))))

  (-update-watching [this derefed]
    (doseq [w derefed]
      (when-not (contains? watching w)
        (-add-watch w this -handle-change)))
    (doseq [w watching]
      (when-not (contains? derefed w)
        (-remove-watch w this)))
    (set! watching derefed)
    nil)

  IRunnable
  (run [this]
    (set! norun? true)
    (let [oldstate state
          res (capture-derefed f this)
          derefed (captured this)]
      (when (not= derefed watching)
        (-update-watching this derefed))
      (set! norun? false)
      (set! dirtyness clean)
      (set! state res)
      (-notify-watches this oldstate state)
      res))

  IDeref
  (-deref [this]
    (-check-clean this)
    (if (or (some? auto-run) (some? *ratom-context*))
      (do
        (notify-deref-watcher! this)
        (if-not (== dirtyness clean)
          (run this)
          state))
      (do
        (when-not (== dirtyness clean)
          (let [oldstate state]
            (set! state (f))
            (when-not (identical? oldstate state)
              (-notify-watches this oldstate state))))
        state)))

  IDisposable
  (dispose! [this]
    (doseq [w watching]
      (remove-watch w this))
    (set! watching nil)
    (set! state nil)
    (set! dirtyness dirty)
    (when on-dispose
      (on-dispose)))

  IEquiv
  (-equiv [o other] (identical? o other))

  IPrintWithWriter
  (-pr-writer [this writer opts]
    (-write writer (str "#<Reaction " (hash this) ": "))
    (pr-writer state writer opts)
    (-write writer ">"))

  IHash
  (-hash [this] (goog/getUid this)))

(defn make-reaction [f & {:keys [auto-run on-set on-dispose derefed]}]
  (let [runner (if (= auto-run true) run auto-run)
        dirty (if (nil? derefed) dirty clean)
        reaction (Reaction. f nil dirty nil nil
                            runner on-set on-dispose false)]
    (when-not (nil? derefed)
      (-update-watching reaction derefed))
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

