
(ns cloact.impl.component
  (:require-macros [cloact.ratom :refer [reaction]]
                   [cloact.debug :refer [dbg prn]])
  (:require [cloact.impl.template :as tmpl]
            [cloact.impl.util :as util]
            [cloact.ratom :as ratom]))

(def React tmpl/React)


;;; Accessors


(defn replace-state [this new-state]
  ;; Don't use React's replaceState, since it doesn't play well
  ;; with clojure maps
  (let [old-state (.-cljsState this)]
    (when-not (identical? old-state new-state)
      (set! (.-cljsState this) new-state)
      (.forceUpdate this))))

(defn set-state [this new-state]
  (replace-state this (merge (.-cljsState this) new-state)))

(defn state [this]
  (.-cljsState this))

;; We store the "args" (i.e a vector like [comp props child1])
;; in .-cljsArgs, with optional props, which makes access a bit
;; tricky. The upside is that we don't have to do any allocations.

(defn args-of [C]
  (-> C (aget "props") .-cljsArgs))

(defn props-in-args [args]
  (let [p (nth args 1 nil)]
    (when (map? p) p)))

(defn props-in-props [props]
  (-> props .-cljsArgs props-in-args))

(defn- first-child [args]
  (let [p (nth args 1 nil)]
    (if (or (nil? p) (map? p)) 2 1)))

(defn- cljs-props [C]
  (-> C args-of props-in-args))

(defn get-children [C]
  (let [args (args-of C)
        c (first-child args)]
    (drop c args)))

(defn replace-props [C newprops]
  (let [obj (js-obj)]
    (set! (.-cljsArgs obj)
          (apply vector
                 (nth (args-of C) 0)
                 newprops
                 (get-children C)))
    (.setProps C obj)))

(defn set-props [C newprops]
  (replace-props C (merge (cljs-props C) newprops)))

(defn get-props [C]
  (cljs-props C))


;;; Function wrapping

(defn do-render [C f]
  (let [res (f (cljs-props C) C)
        conv (if (vector? res)
               (tmpl/as-component res)
               (if (fn? res)
                 (do-render C (set! (.-cljsRenderFn C) res))
                 res))]
    conv))

(defn render [C]
  (assert C)
  (when (nil? (.-cljsRatom C))
    (set! (.-cljsRatom C)
          (reaction :auto-run (if tmpl/isClient
                                #(.forceUpdate C)
                                identity)
                    (do-render C (.-cljsRenderFn C)))))
  (ratom/run (.-cljsRatom C)))

(defn custom-wrapper [key f]
  (case key
    :getDefaultProps
    (assert false "getDefaultProps not supported yet")
    
    :getInitialState
    (fn [C]
      (when f
        (set! (.-cljsState C) (merge (.-cljsState C) (f C)))))

    :componentWillReceiveProps
    (fn [C props]
      (when f (f C (props-in-props props))))

    :shouldComponentUpdate
    (fn [C nextprops nextstate]
      ;; Don't care about nextstate here, we use forceUpdate
      ;; when only when state has changed anyway.
      (let [a1 (args-of C)
            a2 (-> nextprops .-cljsArgs)]
        (assert (vector? a1))
        (if (nil? f)
          (not (util/equal-args a1 a2))
          ;; Call f with oldprops, newprops
          (f (props-in-args a1) (props-in-args a2)))))

    :componentWillUnmount
    (fn [C]
      (ratom/dispose! (.-cljsRatom C))
      (when f (f C)))

    :render
    (fn [C]
      (if (nil? (.-cljsRenderFn C))
        (set! (.-cljsRenderFn C) f))
      (render C))
    nil))

(defn default-wrapper [f]
  (if (fn? f)
    (fn [& args]
      (this-as C (apply f C args)))
    f))

(defn get-wrapper [key f name]
  (let [wrap (custom-wrapper key f)]
    (when (and wrap f)
      (assert (fn? f)
              (str "Expected function in " name key " but got " f)))
    (default-wrapper (or wrap f))))

(def obligatory {:shouldComponentUpdate nil
                 :componentWillUnmount nil})

(defn camelify-map-keys [m]
  (into {} (for [[k v] m]
             [(-> k tmpl/dash-to-camel keyword) v])))

(defn add-obligatory [fun-map]
  (merge obligatory fun-map))

(defn wrap-funs [fun-map]
  (let [name (or (:displayName fun-map)
                 (when-let [r (:render fun-map)]
                   (or (.-displayName r)
                       (.-name r))))
        name1 (if (empty? name) (str (gensym "cloact")) name)]
    (into {} (for [[k v] (assoc fun-map :displayName name1)]
               [k (get-wrapper k v name1)]))))

(defn cljsify [body]
  (-> body
      camelify-map-keys
      add-obligatory
      wrap-funs
      clj->js))

(defn create-class
  [body]
  (assert (map? body))
  (let [spec (cljsify body)
        res (.createClass React spec)
        f (fn [& args]
            (let [arg (js-obj)]
              (set! (.-cljsArgs arg) (apply vector res args))
              (res arg)))]
    (set! (.-cljsReactClass f) res)
    (set! (.-cljsReactClass res) res)
    f))
