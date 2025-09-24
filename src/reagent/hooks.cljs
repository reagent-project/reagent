(ns reagent.hooks
  (:require ["react" :as react]))

(defn use-state [initial-state]
  (react/useState initial-state))

(defn use-reducer [reducer initial-arg init]
  (react/useReducer reducer initial-arg init))

(defn use-ref [v]
  (react/useRef v))

(defn use-effect [setup dependencies]
  (react/useEffect setup dependencies))

(defn use-layout-effect [setup dependencies]
  (react/useLayoutEffect setup dependencies))

(defn use-memo [calculate-value dependencies]
  (react/useMemo calculate-value dependencies))

(defn use-callback [f dependencies]
  (react/useCallback f dependencies))

(defn use-context [ctx]
  (react/useContext ctx))
