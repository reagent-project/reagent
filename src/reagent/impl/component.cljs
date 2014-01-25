
(ns reagent.impl.component
  (:require [reagent.impl.template :as tmpl
             :refer [cljs-props cljs-children React]]
            [reagent.impl.util :as util]
            [reagent.ratom :as ratom]
            [reagent.debug :refer-macros [dbg prn]]
            [clojure.string :as str]))


(def cljs-state "cljsState")

;;; Accessors

(defn state [this]
  (aget this cljs-state))

(defn replace-state [this new-state]
  ;; Don't use React's replaceState, since it doesn't play well
  ;; with clojure maps
  (let [old-state (state this)]
    (when-not (identical? old-state new-state)
      (aset this cljs-state new-state)
      (.forceUpdate this))))

(defn set-state [this new-state]
  (replace-state this (merge (state this) new-state)))

(defn js-props [C]
  (aget C "props"))

(defn props-in-props [props]
  (aget props cljs-props))

(defn get-props [C]
  (-> C js-props props-in-props))

(defn get-children [C]
  (->> C js-props (aget cljs-children)))

(defn replace-props [C newprops]
  (.setProps C (js-obj cljs-props newprops)))

(defn set-props [C newprops]
  (replace-props C (merge (get-props C) newprops)))


;;; Function wrapping

(defn do-render [C f]
  (let [p (js-props C)
        props (props-in-props p)
        children (aget p cljs-children)
        ;; Call render function with props, children, component
        res (f props children C)
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
        (aset C cljs-state (merge (state C) (f C)))))

    :componentWillReceiveProps
    (fn [C props]
      (when f (f C (props-in-props props))))

    :shouldComponentUpdate
    (fn [C nextprops nextstate]
      ;; Don't care about nextstate here, we use forceUpdate
      ;; when only when state has changed anyway.
      (let [inprops (js-props C)
            p1 (aget inprops cljs-props)
            c1 (aget inprops cljs-children)
            p2 (aget nextprops cljs-props)
            c2 (aget nextprops cljs-children)]
        (if (nil? f)
          (not (util/equal-args p1 c1 p2 c2))
          ;; call f with oldprops newprops oldchildren newchildren
          (f C p1 p2 c1 c2))))

    :componentDidUpdate
    (fn [C oldprops]
      (let [inprops (js-props C)
            p (aget inprops cljs-props)
            c (aget inprops cljs-children)]
        (f C p c)))

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

(defn- camelify-map-key? [k]
  (and (keyword? k)
       (let [kname (-> k name str/lower-case)]
         (not-any? #(zero? (.indexOf kname %)) ["data-" "aria-"]))))

(defn camelify-map-keys [m]
  (into {} (for [[k v] m]
             [(if (camelify-map-key? k)
                (-> k tmpl/dash-to-camel keyword)
                k)
              v])))

(defn add-obligatory [fun-map]
  (merge obligatory fun-map))

(defn wrap-funs [fun-map]
  (let [name (or (:displayName fun-map)
                 (when-let [r (:render fun-map)]
                   (or (.-displayName r)
                       (.-name r))))
        name1 (if (empty? name) (str (gensym "reagent")) name)]
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
            (let [props (nth args 0 nil)
                  hasmap (map? props)
                  first-child (if (or hasmap (nil? props)) 1 0)]
              (res (js-obj cljs-props    (if hasmap props)
                           cljs-children (if (> (count args) first-child)
                                           (subvec args first-child))))))]
    (set! (.-cljsReactClass f) res)
    (set! (.-cljsReactClass res) res)
    f))
