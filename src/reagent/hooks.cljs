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
            (if (= new-value current-value)
              current-value
              new-value)))))
    #js [updater]))

(defn- clojure-aware-reducer-updater [reducer]
  (fn [state action]
    (let [new-state (reducer state action)]
      (if (= new-state state)
        state
        new-state))))

;; Hooks

(defn use-action-state
  "Update state based on the result of a form action."
  ([f initial-state]
   (react/useActionState f initial-state))
  ([f initial-state permalink]
   (react/useActionState f initial-state permalink)))

(defn use-callback
  "Cache a function between re-renders.

  Differences to React.js:
  - Dependency comparison is using clojure equality vs React.js uses `Object.is` which would
    only work for JS primitive values."
  [f dependencies]
  (react/useCallback f
                     (array (use-clj-deps dependencies))))

(defn use-context
  "Read and subscribe to React Context."
  [ctx]
  (react/useContext ctx))

(defn use-debug-value
  "Add a label to custom Hook in React Devtools."
  ([value]
   (react/useDebugValue value))
  ([value format]
   (react/useDebugValue value format)))

(defn use-deferred-value
  "Defer updating a part of the UI."
  [value]
  (react/useDeferredValue value))

(defn use-effect
  "Run side-effect on component mount or dependency changes.

  If the setup fn returns a function, that will be used as a cleanup function,
  and will run on unmount, or when depenendecies changes.

  Dependency changes consider Clojure value equality, i.e., you can use
  clojure maps and other data structures in the dependencies vector.

  Differences to React.js:
  - Any non-fn return value from setup fn is considered to mean no cleanup fn is used,
    vs React.js requires `undefined` to be returned.
  - Dependency comparison is using clojure equality vs React.js uses `Object.is` which would
    only work for JS primitive values."
  ([setup]
   (react/useEffect (with-return-value-check setup)))
  ([setup dependencies]
   (react/useEffect (with-return-value-check setup)
                    (array (use-clj-deps dependencies)))))

;; use-effect-event (experimental)
;; (defn use-effect-event [])

(defn use-id
  "Generate unique ID that can be passed to accessibility attributes."
  []
  (react/useId))

(defn use-imperative-handle
  "Customize the handle exposed as a ref."
  ([ref create-handler]
   (react/useImperativeHandle ref create-handler))
  ([ref create-handler dependencies]
   ;; TODO: Should the dependencies use clojure equality here?
   (react/useImperativeHandle ref create-handler dependencies)))

;; For CSS-in-JS library authors, no use exposing here?
;; (defn use-insertion-effect [])

(defn use-layout-effect
  "Version of use-effect that fires before the browser repaints the screen.

  If the setup fn returns a function, that will be used as a cleanup function,
  and will run on unmount, or when depenendecies changes.

  Dependency changes consider Clojure value equality, i.e., you can use
  clojure maps and other data structures in the dependencies vector.

  Differences to React.js:
  - Any non-fn return value from setup fn is considered to mean no cleanup fn is used,
    vs React.js requires `undefined` to be returned.
  - Dependency comparison is using clojure equality vs React.js uses `Object.is` which would
    only work for JS primitive values."
  ([setup]
   (react/useLayoutEffect (with-return-value-check setup)))
  ([setup dependencies]
   (react/useLayoutEffect (with-return-value-check setup)
                          (array (use-clj-deps dependencies)))))

(defn use-memo
  "Cache the result of a calculation between re-renders.

  Dependency changes consider Clojure value equality, i.e., you can use
  clojure maps and other data structures in the dependencies vector.

  Differences to React.js:
  - Dependency comparison is using clojure equality vs React.js uses `Object.is` which would
    only work for JS primitive values."
  [calculate-value dependencies]
  (react/useMemo calculate-value
                 (array (use-clj-deps dependencies))))

(defn use-optimistic
  "Optimistically update the UI"
  [state update-fn]
  (react/useOptimistic state update-fn))

(defn use-reducer
  "Create a state store that is updated by calling reducer with the current
  state and a dispatch event.

  Differences to React.js:
  - The result of reducer fn is compared to the previous state value using
    clojure equality check, and if the value is equal, the previous state
    is kept. This ensures React.js `Object.is` check for checking if the
    state changed works."
  ([reducer initial-arg]
   (react/useReducer (clojure-aware-reducer-updater reducer)
                     initial-arg))
  ([reducer initial-arg init]
   (react/useReducer (clojure-aware-reducer-updater reducer)
                     initial-arg
                     init)))

(defn use-ref
  "Keep a reference to a value that is not needed for rendering."
  [v]
  (react/useRef v))

(defn use-state
  "Create a state store

  Differences to React.js:
  - The new state is compared to the old state using clojure equality,
    and if the value is equal, the previous state is kept. This
    ensures React.js `Object.is` check for checking if the state
    changed works."
  [initial-state]
  (let [[state set-state] (react/useState initial-state)
        set-state (use-clojure-aware-updater set-state)]
    #js [state set-state]))

(defn use-sync-external-store
  "Subscribe to an external store."
  ([subscribe get-snapshot]
   (react/useSyncExternalStore subscribe get-snapshot))
  ([subscribe get-snapshot get-server-snapshot]
   (react/useSyncExternalStore subscribe get-snapshot get-server-snapshot)))

(defn use-transition
  "Render a part of the UI in the background."
  []
  (react/useTransition))
