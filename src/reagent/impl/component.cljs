
(ns reagent.impl.component
  (:require [reagent.impl.template :as tmpl
             :refer [cljs-argv cljs-level React]]
            [reagent.impl.util :as util :refer [cljs-level]]
            [reagent.ratom :as ratom]
            [reagent.debug :refer-macros [dbg prn]]))

(declare ^:dynamic *current-component*)

(def cljs-state "cljsState")
(def cljs-render "cljsRender")

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

(defn extract-props [v]
  (let [p (get v 1)]
    (if (map? p) p)))

(defn extract-children [v]
  (let [p (get v 1)
        first-child (if (or (nil? p) (map? p)) 2 1)]
    (if (> (count v) first-child)
      (subvec v first-child))))

(defn get-argv [C]
  (-> C js-props (aget cljs-argv)))

(defn get-props [C]
  (-> C get-argv extract-props))

(defn get-children [C]
  (-> C get-argv extract-children))


;;; Rendering


(defn do-render [C]
  (binding [*current-component* C]
    (let [f (aget C cljs-render)
          _ (assert (ifn? f))
          p (js-props C)
          res (if (nil? (aget C "componentFunction"))
                (f C)
                (let [argv (aget p cljs-argv)
                      n (count argv)]
                  (case n
                    1 (f)
                    2 (f (argv 1))
                    3 (f (argv 1) (argv 2))
                    4 (f (argv 1) (argv 2) (argv 3))
                    5 (f (argv 1) (argv 2) (argv 3) (argv 4))
                    (apply f (subvec argv 1)))))]
      (if (vector? res)
        (tmpl/as-component res (aget p cljs-level))
        (if (ifn? res)
          (do
            (aset C cljs-render res)
            (do-render C))
          res)))))


;;; Function wrapping

(defn custom-wrapper [key f]
  (case key
    :getDefaultProps
    (assert false "getDefaultProps not supported yet")
    
    :getInitialState
    (fn []
      (this-as C
               (when f
                 (aset C cljs-state (merge (state C) (f C))))))

    :componentWillReceiveProps
    (fn [props]
      (this-as C
               (when f (f C (aget props cljs-argv)))))

    :shouldComponentUpdate
    (fn [nextprops nextstate]
      (this-as C
               ;; Don't care about nextstate here, we use forceUpdate
               ;; when only when state has changed anyway.
               (let [inprops (js-props C)
                     old-argv (aget inprops cljs-argv)
                     new-argv (aget nextprops cljs-argv)]
                 (if (nil? f)
                   (not (util/equal-args old-argv new-argv))
                   (f C old-argv new-argv)))))

    :componentWillUpdate
    (fn [nextprops]
      (this-as C
               (let [next-argv (aget nextprops cljs-argv)]
                 (f C next-argv))))

    :componentDidUpdate
    (fn [oldprops]
      (this-as C
               (let [old-argv (aget oldprops cljs-argv)]
                 (f C old-argv))))

    :componentWillUnmount
    (fn []
      (this-as C
               (util/dispose C)
               (when f (f C))))

    nil))

(defn default-wrapper [f]
  (if (ifn? f)
    (fn [& args]
      (this-as C (apply f C args)))
    f))

(def dont-wrap #{:cljsRender :render})

(defn get-wrapper [key f name]
  (if (dont-wrap key)
    (doto f
      (aset "__reactDontBind" true))
    (let [wrap (custom-wrapper key f)]
      (when (and wrap f)
        (assert (ifn? f)
                (str "Expected function in " name key " but got " f)))
      (or wrap (default-wrapper f)))))

(def obligatory {:shouldComponentUpdate nil
                 :componentWillUnmount nil})

(defn camelify-map-keys [m]
  (into {} (for [[k v] m]
             [(-> k tmpl/dash-to-camel keyword) v])))

(defn add-obligatory [fun-map]
  (merge obligatory fun-map))

(defn add-render [fun-map render-f]
  (assoc fun-map
    :cljsRender render-f
    :render (if util/isClient
              (fn []
                (this-as C
                         (util/run-reactively C #(do-render C))))
              (fn [] (this-as C (do-render C))))))

(defn wrap-funs [fun-map]
  (let [render-fun (or (:componentFunction fun-map)
                       (:render fun-map))
        _ (assert (ifn? render-fun)
                  (str "Render must be a function, not "
                       (pr-str render-fun)))
        name (or (:displayName fun-map)
                 (.-displayName render-fun)
                 (.-name render-fun))
        name' (if (empty? name) (str (gensym "reagent")) name)
        fmap (-> fun-map
                 (assoc :displayName name')
                 (add-render render-fun))]
    (into {} (for [[k v] fmap]
               [k (get-wrapper k v name')]))))

(defn map-to-js [m]
  (reduce-kv (fn [o k v]
               (doto o
                 (aset (name k) v)))
             #js {} m))

(defn cljsify [body]
  (-> body
      camelify-map-keys
      add-obligatory
      wrap-funs
      map-to-js))

(defn create-class
  [body]
  (assert (map? body))
  (let [spec (cljsify body)
        res (.createClass React spec)
        f (fn [& args]
            (tmpl/as-component (apply vector res args)))]
    (set! (.-cljsReactClass f) res)
    (set! (.-cljsReactClass res) res)
    f))
