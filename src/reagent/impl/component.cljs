
(ns reagent.impl.component
  (:require [reagent.impl.util :as util :refer [cljs-level cljs-argv React]]
            [reagent.impl.batching :as batch]
            [reagent.ratom :as ratom]
            [reagent.debug :refer-macros [dbg prn]]))

(declare ^:dynamic *current-component*)

(def cljs-state "cljsState")
(def cljs-render "cljsRender")

;;; State

(defn state-atom [this]
  (let [sa (aget this cljs-state)]
    (if-not (nil? sa)
      sa
      (aset this cljs-state (ratom/atom nil)))))

(defn state [this]
  (deref (state-atom this)))

(defn replace-state [this new-state]
  ;; Don't use React's replaceState, since it doesn't play well
  ;; with clojure maps
  (reset! (state-atom this) new-state))

(defn set-state [this new-state]
  (swap! (state-atom this) merge new-state))


;;; Rendering

(defn do-render [C]
  (binding [*current-component* C]
    (let [f (aget C cljs-render)
          _ (assert (ifn? f))
          p (util/js-props C)
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
        (.asComponent C res (aget p cljs-level))
        (if (ifn? res)
          (do
            (aset C cljs-render res)
            (do-render C))
          res)))))


;;; Method wrapping

(defn custom-wrapper [key f]
  (case key
    :getDefaultProps
    (assert false "getDefaultProps not supported yet")
    
    :getInitialState
    (fn []
      (this-as C
               (set-state C (f C))))

    :componentWillReceiveProps
    (fn [props]
      (this-as C
               (f C (aget props cljs-argv))))

    :shouldComponentUpdate
    (fn [nextprops nextstate]
      (this-as C
               ;; Don't care about nextstate here, we use forceUpdate
               ;; when only when state has changed anyway.
               (let [inprops (util/js-props C)
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
               (batch/dispose C)
               (when-not (nil? f)
                 (f C))))

    nil))

(defn default-wrapper [f]
  (if (ifn? f)
    (fn [& args]
      (this-as C (apply f C args)))
    f))

(def dont-wrap #{:cljsRender :render :componentFunction})

(defn dont-bind [f]
  (if (ifn? f)
    (doto f
      (aset "__reactDontBind" true))
    f))

(defn get-wrapper [key f name]
  (if (dont-wrap key)
    (dont-bind f)
    (let [wrap (custom-wrapper key f)]
      (when (and wrap f)
        (assert (ifn? f)
                (str "Expected function in " name key " but got " f)))
      (or wrap (default-wrapper f)))))

(def obligatory {:shouldComponentUpdate nil
                 :componentWillUnmount nil})

(def dash-to-camel (memoize util/dash-to-camel))

(defn camelify-map-keys [fun-map]
  (reduce-kv (fn [m k v]
               (assoc m (-> k dash-to-camel keyword) v))
             {} fun-map))

(defn add-obligatory [fun-map]
  (merge obligatory fun-map))

(defn add-render [fun-map render-f]
  (assoc fun-map
    :cljsRender render-f
    :render (if util/is-client
              (fn []
                (this-as C
                         (batch/run-reactively C #(do-render C))))
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
    (reduce-kv (fn [m k v]
                 (assoc m k (get-wrapper k v name')))
               {} fmap)))

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
  [body as-component]
  (assert (map? body))
  (let [spec (cljsify body)
        _ (set! (.-asComponent spec) (dont-bind as-component))
        res (.createClass React spec)
        f (fn [& args]
            (as-component (apply vector res args)))]
    (set! (.-cljsReactClass f) res)
    (set! (.-cljsReactClass res) res)
    f))
