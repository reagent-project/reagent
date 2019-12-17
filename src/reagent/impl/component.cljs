(ns reagent.impl.component
  (:require [goog.object :as gobj]
            [react :as react]
            [reagent.impl.util :as util]
            [reagent.impl.batching :as batch]
            [reagent.ratom :as ratom]
            [reagent.debug :refer-macros [dev? warn error warn-unless assert-callable]]))

(declare ^:dynamic *current-component*)


;;; Argv access

(defn shallow-obj-to-map [o]
  (let [ks (js-keys o)
        len (alength ks)]
    (loop [m {}
           i 0]
      (if (< i len)
        (let [k (aget ks i)]
          (recur (assoc m (keyword k) (gobj/get o k))
                 (inc i)))
        m))))

(defn extract-props [v]
  (let [p (nth v 1 nil)]
    (if (map? p) p)))

(defn extract-children [v]
  (let [p (nth v 1 nil)
        first-child (if (or (nil? p) (map? p)) 2 1)]
    (if (> (count v) first-child)
      (subvec v first-child))))

(defn props-argv [^js/React.Component c p]
  (if-some [a (.-argv p)]
    a
    [(.-constructor c) (shallow-obj-to-map p)]))

(defn get-argv [^js/React.Component c]
  (props-argv c (.-props c)))

(defn get-props [^js/React.Component c]
  (let [p (.-props c)]
    (if-some [v (.-argv p)]
      (extract-props v)
      (shallow-obj-to-map p))))

(defn get-children [^js/React.Component c]
  (let [p (.-props c)]
    (if-some [v (.-argv p)]
      (extract-children v)
      (->> (.-children p)
           (react/Children.toArray)
           (into [])))))

(defn ^boolean reagent-class? [c]
  (and (fn? c)
       (some? (some-> c (.-prototype) (.-reagentRender)))))

(defn ^boolean react-class? [c]
  (and (fn? c)
       (some? (some-> c (.-prototype) (.-render)))))

(defn ^boolean reagent-component? [^clj c]
  (some? (.-reagentRender c)))

(defn cached-react-class [^clj c]
  (.-cljsReactClass c))

(defn cache-react-class [^clj c constructor]
  (set! (.-cljsReactClass c) constructor))


;;; State

(defn state-atom [^clj this]
  (let [sa (.-cljsState this)]
    (if-not (nil? sa)
      sa
      (set! (.-cljsState this) (ratom/atom nil)))))

;; avoid circular dependency: this gets set from template.cljs
(defonce as-element nil)


;;; Rendering

(defn wrap-render
  "Calls the render function of the component `c`.  If result `res` evaluates to a:
     1) Vector (form-1 component) - Treats the vector as hiccup and returns
        a react element with a render function based on that hiccup
     2) Function (form-2 component) - updates the render function to `res` i.e. the internal function
        and calls wrap-render again (`recur`), until the render result doesn't evaluate to a function.
     3) Anything else - Returns the result of evaluating `c`"
  [^clj c]
  (let [f (.-reagentRender c)
        _ (assert-callable f)
        ;; cljsLegacyRender tells if this calls was defined
        ;; using :render instead of :reagent-render
        ;; in that case, the :render fn is called with just `this` as argument.
        res (if (true? (.-cljsLegacyRender c))
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
                   (set! (.-reagentRender c) f)
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
     ;; TODO: Use static property for cljsRatom
     (this-as c (if util/*non-reactive*
                  (do-render c)
                  (let [^clj rat (gobj/get c "cljsRatom")]
                    (batch/mark-rendered c)
                    (if (nil? rat)
                      (ratom/run-in-reaction #(do-render c) c "cljsRatom"
                                             batch/queue-render rat-opts)
                      (._run rat false))))))})

(defn custom-wrapper [key f]
  (case key
    :getDefaultProps
    (throw (js/Error. "getDefaultProps not supported"))

    :getDerivedStateFromProps
    (fn getDerivedStateFromProps [props state]
      ;; Read props from Reagent argv
      (.call f nil (if-some [a (.-argv props)] (extract-props a) props) state))

    ;; In ES6 React, this is now part of the constructor
    :getInitialState
    (fn getInitialState [c]
      (reset! (state-atom c) (.call f c c)))

    :getSnapshotBeforeUpdate
    (fn getSnapshotBeforeUpdate [oldprops oldstate]
      (this-as c (.call f c c (props-argv c oldprops) oldstate)))

    ;; Deprecated - warning in 16.9 will work through 17.x
    :componentWillReceiveProps
    (fn componentWillReceiveProps [nextprops]
      (this-as c (.call f c c (props-argv c nextprops))))

    ;; Deprecated - will work in 17.x
    :UNSAFE_componentWillReceiveProps
    (fn componentWillReceiveProps [nextprops]
      (this-as c (.call f c c (props-argv c nextprops))))

    :shouldComponentUpdate
    (fn shouldComponentUpdate [nextprops nextstate]
      (or util/*always-update*
          (this-as c
                   ;; Don't care about nextstate here, we use forceUpdate
                   ;; when only when state has changed anyway.
                   (let [old-argv (.. c -props -argv)
                         new-argv (.-argv nextprops)
                         noargv (or (nil? old-argv) (nil? new-argv))]
                     (cond
                       (nil? f) (or noargv (try (not= old-argv new-argv)
                                                (catch :default e
                                                  (warn "Exception thrown while comparing argv's in shouldComponentUpdate: " old-argv " " new-argv " " e)
                                                  false)))
                       noargv (.call f c c (get-argv c) (props-argv c nextprops))
                       :else  (.call f c c old-argv new-argv))))))

    ;; Deprecated - warning in 16.9 will work through 17.x
    :componentWillUpdate
    (fn componentWillUpdate [nextprops nextstate]
      (this-as c (.call f c c (props-argv c nextprops) nextstate)))

    ;; Deprecated - will work in 17.x
    :UNSAFE_componentWillUpdate
    (fn componentWillUpdate [nextprops nextstate]
      (this-as c (.call f c c (props-argv c nextprops) nextstate)))

    :componentDidUpdate
    (fn componentDidUpdate [oldprops oldstate snapshot]
      (this-as c (.call f c c (props-argv c oldprops) oldstate snapshot)))

    ;; Deprecated - warning in 16.9 will work through 17.x
    :componentWillMount
    (fn componentWillMount []
      (this-as c (.call f c c)))

    ;; Deprecated - will work in 17.x
    :UNSAFE_componentWillMount
    (fn componentWillMount []
      (this-as c (.call f c c)))

    :componentDidMount
    (fn componentDidMount []
      (this-as c (.call f c c)))

    :componentWillUnmount
    (fn componentWillUnmount []
      (this-as c
               (some-> (gobj/get c "cljsRatom") ratom/dispose!)
               (batch/mark-rendered c)
               (when-not (nil? f)
                 (.call f c c))))

    :componentDidCatch
    (fn componentDidCatch [error info]
      (this-as c (.call f c c error info)))

    nil))

(defn get-wrapper [key f]
  (let [wrap (custom-wrapper key f)]
    (when (and wrap f)
      (assert-callable f))
    (or wrap f)))

;; Though the value is nil here, the wrapper function will be
;; added to class to manage Reagent ratom lifecycle.
(def obligatory {:shouldComponentUpdate nil
                 :componentWillUnmount nil})

(def dash-to-method-name (util/memoize-1 util/dash-to-method-name))

(defn camelify-map-keys [fun-map]
  (reduce-kv (fn [m k v]
               (assoc m (-> k dash-to-method-name keyword) v))
             {} fun-map))

(defn add-obligatory [fun-map]
  (merge obligatory fun-map))

(defn wrap-funs [fmap]
  (when (dev?)
    (let [renders (select-keys fmap [:render :reagentRender])
          render-fun (-> renders vals first)]
      (assert (not (:componentFunction fmap)) ":component-function is no longer supported, use :reagent-render instead.")
      (assert (pos? (count renders)) "Missing reagent-render")
      (assert (== 1 (count renders)) "Too many render functions supplied")
      (assert-callable render-fun)))
  (let [render-fun (or (:reagentRender fmap)
                       (:render fmap))
        legacy-render (nil? (:reagentRender fmap))
        name (or (:displayName fmap)
                 (util/fun-name render-fun)
                 (str (gensym "reagent")))
        fmap (reduce-kv (fn [m k v]
                          (assoc m k (get-wrapper k v)))
                        {} fmap)]
    (assoc fmap
           :displayName name
           :cljsLegacyRender legacy-render
           :reagentRender render-fun
           :render (:render static-fns))))

(defn map-to-js [m]
  (reduce-kv (fn [o k v]
               (doto o
                 (gobj/set (name k) v)))
             #js{} m))

(defn cljsify [body]
  (-> body
      camelify-map-keys
      add-obligatory
      wrap-funs))

;; Idea from:
;; https://gist.github.com/pesterhazy/2a25c82db0519a28e415b40481f84554
;; https://gist.github.com/thheller/7f530b34de1c44589f4e0671e1ef7533#file-es6-class-cljs-L18

(def built-in-static-method-names
  [:childContextTypes :contextTypes :contextType
   :getDerivedStateFromProps :getDerivedStateFromError])

(defn create-class
  "Creates JS class based on provided Clojure map.

  Map keys should use `React.Component` method names (https://reactjs.org/docs/react-component.html),
  and can be provided in snake-case or camelCase.
  Constructor function is defined using key `:getInitialState`.

  React built-in static methods or properties are automatically defined as statics."
  [body]
  {:pre [(map? body)]}
  (let [body (cljsify body)
        methods (map-to-js (apply dissoc body :displayName :getInitialState :constructor
                                  :render :reagentRender
                                  built-in-static-method-names))
        static-methods (map-to-js (select-keys body built-in-static-method-names))
        display-name (:displayName body)
        get-initial-state (:getInitialState body)
        construct (:constructor body)
        cmp (fn [props context updater]
              (this-as this
                (.call react/Component this props context updater)
                (when construct
                  (construct this props))
                (when get-initial-state
                  (set! (.-state this) (get-initial-state this)))
                (set! (.-cljsMountOrder ^clj this) (batch/next-mount-count))
                this))]

    (gobj/extend (.-prototype cmp) (.-prototype react/Component) methods)

    ;; These names SHOULD be mangled by Closure so we can't use goog/extend

    (when (:render body)
      (set! (.-render ^js (.-prototype cmp)) (:render body)))

    (when (:reagentRender body)
      (set! (.-reagentRender ^clj (.-prototype cmp)) (:reagentRender body)))

    (when (:cljsLegacyRender body)
      (set! (.-cljsLegacyRender ^clj (.-prototype cmp)) (:cljsLegacyRender body)))

    (gobj/extend cmp react/Component static-methods)

    (when display-name
      (set! (.-displayName cmp) display-name)
      (set! (.-cljs$lang$ctorStr cmp) display-name)
      (set! (.-cljs$lang$ctorPrWriter cmp)
            (fn [this writer opt]
              (cljs.core/-write writer display-name))))

    (set! (.-cljs$lang$type cmp) true)
    (set! (.. cmp -prototype -constructor) cmp)

    cmp))

(defn fiber-component-path [fiber]
  (let [name (some-> fiber
                     (.-type)
                     (.-displayName))
        parent (some-> fiber
                       (.-return))
        path (some-> parent
                     fiber-component-path
                     (str " > "))
        res (str path name)]
    (when-not (empty? res) res)))

(defn component-path [c]
  ;; Alternative branch for React 16
  ;; Try both original name (for UMD foreign-lib) and manged name (property access, for Closure optimized React)
  (if-let [fiber (or (some-> c (gobj/get "_reactInternalFiber"))
                     (some-> c (.-_reactInternalFiber)))]
    (fiber-component-path fiber)
    (let [instance (or (some-> c (gobj/get "_reactInternalInstance"))
                       (some-> c (.-_reactInternalInstance))
                       c)
          elem (or (some-> instance (gobj/get "_currentElement"))
                   (some-> instance (.-_currentElement)))
          name (some-> elem
                       (.-type)
                       (.-displayName))
          owner (or (some-> elem (gobj/get "_owner"))
                    (some-> elem (.-_owner)))
          path (some-> owner
                       component-path
                       (str " > "))
          res (str path name)]
      (when-not (empty? res) res))))

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
  (assert-callable f)
  (warn-unless (not (and (react-class? f)
                         (not (reagent-class? f))))
               "Using native React classes directly in Hiccup forms "
               "is not supported. Use create-element or "
               "adapt-react-class instead: " (or (util/fun-name f)
                                                 f)
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
