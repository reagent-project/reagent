(ns reagent.ratom
  (:refer-clojure :exclude [atom])
  (:require-macros [reagent.debug :refer (dbg)]))

(declare ^:dynamic *ratom-context*)

(def debug false)

(def -running (clojure.core/atom 0))

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

(deftype RAtom [^:mutable state meta validator ^:mutable watches]
  IAtom

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
    (set! watches (assoc watches key f)))
  (-remove-watch [this key]
    (set! watches (dissoc watches key)))

  IHash
  (-hash [this] (goog/getUid this)))

(defn atom
  "Like clojure.core/atom, except that it keeps track of derefs."
  ([x] (RAtom. x nil nil nil))
  ([x & {:keys [meta validator]}] (RAtom. x meta validator nil)))

(deftype RCursor [path ratom]
  IAtom

  IEquiv
  (-equiv [o other] (identical? o other))

  IDeref
  (-deref [this]
    (get-in @ratom path))

  IReset
  (-reset! [a new-value]
    (swap! ratom assoc-in path new-value))

  ISwap
  (-swap! [a f]
    (swap! ratom update-in path f))
  (-swap! [a f x]
    (swap! ratom update-in path f x))
  (-swap! [a f x y]
    (swap! ratom update-in path f x y))
  (-swap! [a f x y more]
    (swap! ratom update-in path f x y more))

  IMeta
  (-meta [_]
    (-meta ratom))

  IPrintWithWriter
  (-pr-writer [a writer opts]
    ;; not sure about how this should be implemented?
    ;; should it print as an atom focused on the appropriate part of
    ;; the ratom - (pr-writer (get-in @ratom path)) - or should it be
    ;; a completely separate type? and do we need a reader for it?
    (-write writer "#<Cursor: ")
    (pr-writer path writer opts)
    (-write writer " ")
    (pr-writer ratom writer opts)
    (-write writer ">"))

  IWatchable
  (-notify-watches [this oldval newval]
    (-notify-watches ratom oldval newval))
  (-add-watch [this key f]
    (-add-watch ratom key f))
  (-remove-watch [this key]
    (-remove-watch ratom key))

  IHash
  (-hash [this] (goog/getUid this)))

(defn cursor
  [path ra]
  (RCursor. path ra))

(defprotocol IDisposable
  (dispose! [this]))

(defprotocol IRunnable
  (run [this]))

(defprotocol IComputedImpl
  (-update-watching [this derefed])
  (-handle-change [k sender oldval newval]))

(defn- call-watches [obs watches oldval newval]
  (reduce-kv (fn [_ key f]
               (f key obs oldval newval)
               nil)
             nil watches))

(deftype Reaction [f ^:mutable state ^:mutable dirty? ^:mutable active?
                   ^:mutable watching ^:mutable watches
                   auto-run on-set on-dispose]
  IAtom

  IWatchable
  (-notify-watches [this oldval newval]
    (when on-set
      (on-set oldval newval))
    (call-watches this watches oldval newval))

  (-add-watch [this k wf]
    (set! watches (assoc watches k wf)))

  (-remove-watch [this k]
    (set! watches (dissoc watches k))
    (when (empty? watches)
      (dispose! this)))

  IReset
  (-reset! [a new-value]
    (let [old-value state]
      (set! state new-value)
      (-notify-watches a old-value new-value)
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

  IComputedImpl
  (-handle-change [this sender oldval newval]
    (when (and active? (not dirty?) (not (identical? oldval newval)))
      (set! dirty? true)
      ((or auto-run run) this)))

  (-update-watching [this derefed]
    (doseq [w derefed]
      (when-not (contains? watching w)
        (add-watch w this -handle-change)))
    (doseq [w watching]
      (when-not (contains? derefed w)
        (remove-watch w this)))
    (set! watching derefed))

  IRunnable
  (run [this]
    (let [oldstate state
          res (capture-derefed f this)
          derefed (captured this)]
      (when (not= derefed watching)
        (-update-watching this derefed))
      (when-not active?
        (when debug (swap! -running inc))
        (set! active? true))
      (set! dirty? false)
      (set! state res)
      (call-watches this watches oldstate state)
      res))

  IDeref
  (-deref [this]
    ;; TODO: relax this?
    (when (not (or auto-run *ratom-context*))
      (dbg [auto-run *ratom-context*]))
    (assert (or auto-run *ratom-context*)
            "Reaction derefed outside auto-running context")
    (notify-deref-watcher! this)
    (if dirty?
      (run this)
      state))

  IDisposable
  (dispose! [this]
    (doseq [w watching]
      (remove-watch w this))
    (set! watching #{})
    (set! state nil)
    (set! dirty? true)
    (when active?
      (when debug (swap! -running dec))
      (set! active? false))
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
        active (not (nil? derefed))
        dirty (not active)
        reaction (Reaction. f nil dirty active
                            nil {}
                            runner on-set on-dispose)]
    (when-not (nil? derefed)
      (when debug (swap! -running inc))
      (-update-watching reaction derefed))
    reaction))
