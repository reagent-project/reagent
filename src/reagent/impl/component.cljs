
(ns reagent.impl.component
  (:require [reagent.impl.util :as util]
            [reagent.impl.batching :as batch]
            [reagent.ratom :as ratom]
            [reagent.interop :refer-macros [.' .!]]
            [reagent.debug :refer-macros [dbg prn]]))

(declare ^:dynamic *current-component*)

;;; State

(defn state-atom [this]
  (let [sa (.' this :cljsState)]
    (if-not (nil? sa)
      sa
      (.! this :cljsState (ratom/atom nil)))))

(defn state [this]
  (deref (state-atom this)))

(defn replace-state [this new-state]
  ;; Don't use React's replaceState, since it doesn't play well
  ;; with clojure maps
  (reset! (state-atom this) new-state))

(defn set-state [this new-state]
  (swap! (state-atom this) merge new-state))


;;; Rendering

(defn do-render [c]
  (binding [*current-component* c]
    (let [f (.' c :cljsRender)
          _ (assert (util/clj-ifn? f))
          p (.' c :props)
          res (if (nil? (.' c :componentFunction))
                (f c)
                (let [argv (.' p :argv)
                      n (count argv)]
                  (case n
                    1 (f)
                    2 (f (nth argv 1))
                    3 (f (nth argv 1) (nth argv 2))
                    4 (f (nth argv 1) (nth argv 2) (nth argv 3))
                    5 (f (nth argv 1) (nth argv 2) (nth argv 3) (nth argv 4))
                    (apply f (subvec argv 1)))))]
      (if (vector? res)
        (.' c asComponent res (.' p :level))
        (if (ifn? res)
          (do
            (.! c :cljsRender res)
            (do-render c))
          res)))))


;;; Method wrapping

(defn custom-wrapper [key f]
  (case key
    :getDefaultProps
    (assert false "getDefaultProps not supported yet")

    :getInitialState
    (fn []
      (this-as c
               (set-state c (f c))))

    :componentWillReceiveProps
    (fn [props]
      (this-as c
               (f c (.' props :argv))))

    :shouldComponentUpdate
    (fn [nextprops nextstate]
      (or util/*always-update*
          (this-as c
                   ;; Don't care about nextstate here, we use forceUpdate
                   ;; when only when state has changed anyway.
                   (let [old-argv (.' c :props.argv)
                         new-argv (.' nextprops :argv)]
                     (if (nil? f)
                       (not (util/equal-args old-argv new-argv))
                       (f c old-argv new-argv))))))

    :componentWillUpdate
    (fn [nextprops]
      (this-as c
               (f c (.' nextprops :argv))))

    :componentDidUpdate
    (fn [oldprops]
      (this-as c
               (f c (.' oldprops :argv))))

    :componentWillUnmount
    (fn []
      (this-as c
               (batch/dispose c)
               (when-not (nil? f)
                 (f c))))

    nil))

(defn default-wrapper [f]
  (if (ifn? f)
    (fn [& args]
      (this-as c (apply f c args)))
    f))

(def dont-wrap #{:cljsRender :render :componentFunction})

(defn dont-bind [f]
  (if (ifn? f)
    (doto f
      (.! :__reactDontBind true))
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

(def dash-to-camel (util/memoize-1 util/dash-to-camel))

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
                (this-as c
                         (batch/run-reactively c #(do-render c))))
              (fn [] (this-as c (do-render c))))))

(defn wrap-funs [fun-map]
  (let [render-fun (or (:componentFunction fun-map)
                       (:render fun-map))
        _ (assert (util/clj-ifn? render-fun)
                  (str "Render must be a function, not "
                       (pr-str render-fun)))
        name (or (:displayName fun-map)
                 (.' render-fun :displayName)
                 (.' render-fun :name))
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
             #js{} m))

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
        _ (.! spec :asComponent (dont-bind as-component))
        res (.' js/React createClass spec)
        f (fn [& args]
            (as-component (apply vector res args)))]
    (util/cache-react-class f res)
    (util/cache-react-class res res)
    f))
