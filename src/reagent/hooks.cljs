(ns reagent.hooks
  (:require ["react" :as react]))

;; Helpers to make hooks use clojure value equality etc.
;; These are copied from (with permission) or inspired by UIx implementation

(defn with-return-value-check
  "Consider any non-fn value from effect callbacks as no callback."
  [f]
  (fn []
    (let [ret (f)]
      (if (fn? ret)
        ret
        js/undefined))))

(defn use-clj-deps
  "Check the new clj deps value against the previous value, stored in a
  ref hook, so we can use clj equality check to see if the value changed.

  Only not= value is stored to the ref, so the effect dependency value
  only updates when the equality changed."
  [deps]
  (let [ref (react/useRef deps)]
    (when (not= (.-current ref) deps)
      (set! (.-current ref) deps))
    (.-current ref)))

(defn- use-clojure-aware-updater
  "When update fn returns the equal clj value as the previous value,
  keep using the old value, to keep the identity stable."
  [updater]
  (react/useCallback
    (fn [v & args]
      (updater
        (fn [current-value]
          (let [new-value (if (fn? v)
                            (apply v current-value args)
                            v)]
            (js/console.log (pr-str current-value) (pr-str new-value) (= new-value current-value))
            (if (= new-value current-value)
              current-value
              new-value)))))
    #js [updater]))

;; Hooks

(defn use-state [initial-state]
  (let [[state set-state] (react/useState initial-state)
        set-state (use-clojure-aware-updater set-state)]
    #js [state set-state]))

(defn use-reducer [reducer initial-arg init]
  (react/useReducer (fn [state action]
                      (let [new-state (reducer state action)]
                        (if (= new-state state)
                          state
                          new-state)))
                    initial-arg
                    init))

(defn use-ref [v]
  (react/useRef v))

(defn use-effect [setup dependencies]
  (react/useEffect (with-return-value-check setup)
                   (array (use-clj-deps dependencies))))

(defn use-layout-effect [setup dependencies]
  (react/useLayoutEffect (with-return-value-check setup)
                         (array (use-clj-deps dependencies))))

(defn use-memo [calculate-value dependencies]
  (react/useMemo calculate-value
                 (array (use-clj-deps dependencies))))

(defn use-callback [f dependencies]
  (react/useCallback f
                     (array (use-clj-deps dependencies))))

(defn use-context [ctx]
  (react/useContext ctx))
