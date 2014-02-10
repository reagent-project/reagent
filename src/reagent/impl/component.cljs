
(ns reagent.impl.component
  (:refer-clojure :exclude [flush])
  (:require [reagent.impl.template :as tmpl
             :refer [cljs-argv cljs-level React]]
            [reagent.impl.util :as util]
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

(defn fake-raf [f]
  (js/setTimeout f 16))

(def next-tick
  (if-not tmpl/isClient
    fake-raf
    (let [w js/window]
      (or (.-requestAnimationFrame w)
          (.-webkitRequestAnimationFrame w)
          (.-mozRequestAnimationFrame w)
          (.-msRequestAnimationFrame w)
          fake-raf))))

(defn compare-levels [c1 c2]
  (- (-> c1 js-props (aget cljs-level))
     (-> c2 js-props (aget cljs-level))))

(defn run-queue [a]
  ;; sort components by level, to make sure parents
  ;; are rendered before children
  (.sort a compare-levels)
  (dotimes [i (alength a)]
    (let [C (aget a i)]
      (when (.-cljsIsDirty C)
        (.forceUpdate C)))))

(deftype RenderQueue [^:mutable queue ^:mutable scheduled?]
  Object
  (queue-render [this C]
    (.push queue C)
    (.schedule this))
  (schedule [this]
    (when-not scheduled?
      (set! scheduled? true)
      (next-tick #(.run-queue this))))
  (run-queue [_]
    (let [q queue]
      (set! queue (array))
      (set! scheduled? false)
      (run-queue q))))

(def render-queue (RenderQueue. (array) false))

(defn flush []
  (.run-queue render-queue))

(defn queue-render [C]
  (set! (.-cljsIsDirty C) true)
  (.queue-render render-queue C))

(defn do-render [C]
  (set! (.-cljsIsDirty C) false)
  (binding [*current-component* C]
    (let [f (aget C cljs-render)
          _ (assert (fn? f))
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
        (if (fn? res)
          (do
            (aset C cljs-render res)
            (do-render C))
          res)))))

(defn reactive-render [C]
  (assert C)
  (when (nil? (.-cljsRatom C))
    (set! (.-cljsRatom C)
          (ratom/make-reaction
           #(do-render C)
           :auto-run (if tmpl/isClient
                       #(queue-render C)
                       identity))))
  (ratom/run (.-cljsRatom C)))


;;; Function wrapping

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
      (when f (f C (aget props cljs-argv))))

    :shouldComponentUpdate
    (fn [C nextprops nextstate]
      ;; Don't care about nextstate here, we use forceUpdate
      ;; when only when state has changed anyway.
      (let [inprops (js-props C)
            old-argv (aget inprops cljs-argv)
            new-argv (aget nextprops cljs-argv)]
        (if (nil? f)
          (not (util/equal-args old-argv new-argv))
          (f C old-argv new-argv))))

    :componentWillUpdate
    (fn [C nextprops]
      (let [next-argv (aget nextprops cljs-argv)]
        (f C next-argv)))

    :componentDidUpdate
    (fn [C oldprops]
      (let [old-argv (aget oldprops cljs-argv)]
        (f C old-argv)))

    :componentWillUnmount
    (fn [C]
      (ratom/dispose! (.-cljsRatom C))
      (set! (.-cljsIsDirty C) false)
      (when f (f C)))

    nil))

(defn default-wrapper [f]
  (if (fn? f)
    (fn [& args]
      (this-as C (apply f C args)))
    f))

(def dont-wrap #{:cljsRender})

(defn get-wrapper [key f name]
  (if (dont-wrap key)
    (doto f
      (aset "__reactDontBind" true))
    (let [wrap (custom-wrapper key f)]
      (when (and wrap f)
        (assert (fn? f)
                (str "Expected function in " name key " but got " f)))
      (default-wrapper (or wrap f)))))

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
    :render reactive-render))

(defn wrap-funs [fun-map]
  (let [render-fun (or (:componentFunction fun-map)
                       (:render fun-map))
        _ (assert (ifn? render-fun))
        name (or (:displayName fun-map)
                 (.-displayName render-fun)
                 (.-name render-fun))
        name' (if (empty? name) (str (gensym "reagent")) name)
        fmap (-> fun-map
                 (assoc :displayName name')
                 (add-render render-fun))]
    (into {} (for [[k v] fmap]
               [k (get-wrapper k v name')]))))

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
            (tmpl/as-component (apply vector res args)))]
    (set! (.-cljsReactClass f) res)
    (set! (.-cljsReactClass res) res)
    f))
