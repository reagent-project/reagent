
(ns cloact.impl.component
  (:require [cloact.impl.template :as tmpl]
            [cloact.impl.util :as util]
            [cloact.ratom :as ratom]
            [cloact.debug :refer-macros [dbg prn]]))

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

(defn js-props [C]
  (aget C "props"))

(defn props-in-props [props]
  (-> props .-cljsProps))

(defn cljs-props [C]
  (-> C js-props props-in-props))

(defn get-children [C]
  (-> C js-props .-cljsChildren))

(defn replace-props [C newprops]
  (let [obj (js-obj)]
    (set! (.-cljsProps obj) newprops)
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
          (ratom/make-reaction
           #(do-render C (.-cljsRenderFn C))
           :auto-run (if tmpl/isClient
                                #(.forceUpdate C)
                                identity))))
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
      (let [inprops (aget C "props")
            p1 (.-cljsProps inprops)
            c1 (.-cljsChildren inprops)
            p2 (.-cljsProps nextprops)
            c2 (.-cljsChildren nextprops)]
        (if (nil? f)
          (not (util/equal-args p1 c1 p2 c2))
          ;; call f with oldprops newprops oldchildren newchildren
          (f p1 p2 c1 c2))))

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
            (let [arg (js-obj)
                  props (nth args 0 nil)
                  hasmap (map? props)
                  first-child (if (or hasmap (nil? props)) 1 0)]
              (set! (.-cljsProps arg) (if hasmap props {}))
              (set! (.-cljsChildren arg)
                    (if (> (count args) first-child)
                      (subvec args first-child)))
              (res arg)))]
    (set! (.-cljsReactClass f) res)
    (set! (.-cljsReactClass res) res)
    f))
