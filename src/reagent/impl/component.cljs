(ns reagent.impl.component
  (:require [reagent.impl.util :as util]
            [reagent.impl.batching :as batch]
            [reagent.ratom :as ratom]
            [reagent.interop :refer-macros [$ $!]]
            [reagent.debug :refer-macros [dbg prn dev? warn error warn-unless]]))

(declare ^:dynamic *current-component*)


;;; Argv access

(defn shallow-obj-to-map [o]
  (let [ks (js-keys o)
        len (alength ks)]
    (loop [m {} i 0]
      (if (< i len)
        (let [k (aget ks i)]
          (recur (assoc m (keyword k) (aget o k)) (inc i)))
        m))))

(defn extract-props [v]
  (let [p (nth v 1 nil)]
    (if (map? p) p)))

(defn extract-children [v]
  (let [p (nth v 1 nil)
        first-child (if (or (nil? p) (map? p)) 2 1)]
    (if (> (count v) first-child)
      (subvec v first-child))))

(defn props-argv [c p]
  (if-some [a ($ p :argv)]
    a
    [(.-constructor c) (shallow-obj-to-map p)]))

(defn get-argv [c]
  (props-argv c ($ c :props)))

(defn get-props [c]
  (let [p ($ c :props)]
    (if-some [v ($ p :argv)]
      (extract-props v)
      (shallow-obj-to-map p))))

(defn get-children [c]
  (let [p ($ c :props)]
    (if-some [v ($ p :argv)]
      (extract-children v)
      (->> ($ p :children)
           ($ util/react Children.toArray)
           (into [])))))

(defn ^boolean reagent-class? [c]
  (and (fn? c)
       (some? (some-> c .-prototype ($ :reagentRender)))))

(defn ^boolean react-class? [c]
  (and (fn? c)
       (some? (some-> c .-prototype ($ :render)))))

(defn ^boolean reagent-component? [c]
  (some? ($ c :reagentRender)))

(defn cached-react-class [c]
  ($ c :cljsReactClass))

(defn cache-react-class [c constructor]
  ($! c :cljsReactClass constructor))


;;; State

(defn state-atom [this]
  (let [sa ($ this :cljsState)]
    (if-not (nil? sa)
      sa
      ($! this :cljsState (ratom/atom nil)))))

;; avoid circular dependency: this gets set from template.cljs
(defonce as-element nil)


;;; Rendering

(defn wrap-render [c]
  (let [f ($ c :reagentRender)
        _ (assert (ifn? f))
        res (if (true? ($ c :cljsLegacyRender))
              (.call f c c)
              (let [v (get-argv c)
                    n (count v)]
                (case n
                  1 (.call f c)
                  2 (.call f c (nth v 1))
                  3 (.call f c (nth v 1) (nth v 2))
                  4 (.call f c (nth v 1) (nth v 2) (nth v 3))
                  5 (.call f c (nth v 1) (nth v 2) (nth v 3) (nth v 4))
                  (.apply f c (.slice (into-array v) 1)))))]
    (cond
      (vector? res) (as-element res)
      (ifn? res) (let [f (if (reagent-class? res)
                           (fn [& args]
                             (as-element (apply vector res args)))
                           res)]
                   ($! c :reagentRender f)
                   (recur c))
      :else res)))

(declare comp-name)

(defn do-render [c]
  (binding [*current-component* c]
    (if (dev?)
      ;; Log errors, without using try/catch (and mess up call stack)
      (let [ok (array false)]
        (try
          (let [res (wrap-render c)]
            (aset ok 0 true)
            res)
          (finally
            (when-not (aget ok 0)
              (error (str "Error rendering component"
                          (comp-name)))))))
      (wrap-render c))))


;;; Method wrapping

(def rat-opts {:no-cache true})

(def static-fns
  {:render
   (fn render []
     (this-as c (if util/*non-reactive*
                  (do-render c)
                  (let [rat ($ c :cljsRatom)]
                    (batch/mark-rendered c)
                    (if (nil? rat)
                      (ratom/run-in-reaction #(do-render c) c "cljsRatom"
                                             batch/queue-render rat-opts)
                      (._run rat false))))))})

(defn custom-wrapper [key f]
  (case key
    :getDefaultProps
    (assert false "getDefaultProps not supported")

    :getInitialState
    (fn getInitialState []
      (this-as c (reset! (state-atom c) (.call f c c))))

    :componentWillReceiveProps
    (fn componentWillReceiveProps [nextprops]
      (this-as c (.call f c c (props-argv c nextprops))))

    :shouldComponentUpdate
    (fn shouldComponentUpdate [nextprops nextstate]
      (or util/*always-update*
          (this-as c
                   ;; Don't care about nextstate here, we use forceUpdate
                   ;; when only when state has changed anyway.
                   (let [old-argv ($ c :props.argv)
                         new-argv ($ nextprops :argv)
                         noargv (or (nil? old-argv) (nil? new-argv))]
                     (cond
                       (nil? f) (or noargv (not= old-argv new-argv))
                       noargv (.call f c c (get-argv c) (props-argv c nextprops))
                       :else  (.call f c c old-argv new-argv))))))

    :componentWillUpdate
    (fn componentWillUpdate [nextprops]
      (this-as c (.call f c c (props-argv c nextprops))))

    :componentDidUpdate
    (fn componentDidUpdate [oldprops]
      (this-as c (.call f c c (props-argv c oldprops))))

    :componentWillMount
    (fn componentWillMount []
      (this-as c
               ($! c :cljsMountOrder (batch/next-mount-count))
               (when-not (nil? f)
                 (.call f c c))))

    :componentDidMount
    (fn componentDidMount []
      (this-as c (.call f c c)))

    :componentWillUnmount
    (fn componentWillUnmount []
      (this-as c
               (some-> ($ c :cljsRatom)
                       ratom/dispose!)
               (batch/mark-rendered c)
               (when-not (nil? f)
                 (.call f c c))))

    nil))

(defn get-wrapper [key f name]
  (let [wrap (custom-wrapper key f)]
    (when (and wrap f)
      (assert (ifn? f)
              (str "Expected function in " name key " but got " f)))
    (or wrap f)))

(def obligatory {:shouldComponentUpdate nil
                 :componentWillMount nil
                 :componentWillUnmount nil})

(def dash-to-camel (util/memoize-1 util/dash-to-camel))

(defn camelify-map-keys [fun-map]
  (reduce-kv (fn [m k v]
               (assoc m (-> k dash-to-camel keyword) v))
             {} fun-map))

(defn add-obligatory [fun-map]
  (merge obligatory fun-map))

(defn wrap-funs [fmap]
  (when (dev?)
    (let [renders (select-keys fmap [:render :reagentRender :componentFunction])
          render-fun (-> renders vals first)]
      (assert (pos? (count renders)) "Missing reagent-render")
      (assert (== 1 (count renders)) "Too many render functions supplied")
      (assert (ifn? render-fun) (str "Render must be a function, not "
                                     (pr-str render-fun)))))
  (let [render-fun (or (:reagentRender fmap)
                       (:componentFunction fmap))
        legacy-render (nil? render-fun)
        render-fun (or render-fun
                       (:render fmap))
        name (str (or (:displayName fmap)
                      (util/fun-name render-fun)))
        name (case name
               "" (str (gensym "reagent"))
               name)
        fmap (reduce-kv (fn [m k v]
                          (assoc m k (get-wrapper k v name)))
                        {} fmap)]
    (assoc fmap
           :displayName name
           :autobind false
           :cljsLegacyRender legacy-render
           :reagentRender render-fun
           :render (:render static-fns))))

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

(defn create-class [body]
  {:pre [(map? body)]}
  (->> body
       cljsify
       ($ util/react createClass)))

(defn component-path [c]
  (let [elem (some-> (or (some-> c ($ :_reactInternalInstance))
                          c)
                     ($ :_currentElement))
        name (some-> elem
                     ($ :type)
                     ($ :displayName))
        path (some-> elem
                     ($ :_owner)
                     component-path
                     (str " > "))
        res (str path name)]
    (when-not (empty? res) res)))

(defn comp-name []
  (if (dev?)
    (let [c *current-component*
          n (or (component-path c)
                (some-> c .-constructor util/fun-name))]
      (if-not (empty? n)
        (str " (in " n ")")
        ""))
    ""))

(defn fn-to-class [f]
  (assert (ifn? f) (str "Expected a function, not " (pr-str f)))
  (warn-unless (not (and (react-class? f)
                         (not (reagent-class? f))))
               "Using native React classes directly in Hiccup forms "
               "is not supported. Use create-element or "
               "adapt-react-class instead: " (let [n (util/fun-name f)]
                                               (if (empty? n) f n))
               (comp-name))
  (if (reagent-class? f)
    (cache-react-class f f)
    (let [spec (meta f)
          withrender (assoc spec :reagent-render f)
          res (create-class withrender)]
      (cache-react-class f res))))

(defn as-class [tag]
  (if-some [cached-class (cached-react-class tag)]
    cached-class
    (fn-to-class tag)))

(defn reactify-component [comp]
  (if (react-class? comp)
    comp
    (as-class comp)))
