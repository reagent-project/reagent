(ns reagent.impl.component
  (:require [goog.object :as gobj]
            [react :as react]
            [reagent.impl.util :as util]
            [reagent.impl.batching :as batch]
            [reagent.impl.protocols :as p]
            [reagent.ratom :as ratom]
            [reagent.debug :refer-macros [dev? warn warn-unless assert-callable]]))

(declare ^:dynamic *current-component*)


;;; Argv access

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
    [(.-constructor c) (util/shallow-obj-to-map p)]))

(defn get-argv [^js/React.Component c]
  (props-argv c (.-props c)))

(defn get-props [^js/React.Component c]
  (let [p (.-props c)]
    (if-some [v (.-argv p)]
      (extract-props v)
      (util/shallow-obj-to-map p))))

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

;;; State

(defn state-atom [^clj this]
  (let [sa (.-cljsState this)]
    (if-not (nil? sa)
      sa
      (set! (.-cljsState this) (ratom/atom nil)))))

;;; Rendering

(defn wrap-render
  "Calls the render function of the component `c`.  If result `res` evaluates to a:
     1) Vector (form-1 component) - Treats the vector as hiccup and returns
        a react element with a render function based on that hiccup
     2) Function (form-2 component) - updates the render function to `res` i.e. the internal function
        and calls wrap-render again (`recur`), until the render result doesn't evaluate to a function.
     3) Anything else - Returns the result of evaluating `c`"
  [^clj c compiler]
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
      (vector? res) (p/as-element compiler res)
      (ifn? res) (let [f (if (reagent-class? res)
                           (fn [& args]
                             (p/as-element compiler (apply vector res args)))
                           res)]
                   (set! (.-reagentRender c) f)
                   (recur c compiler))
      :else res)))

(defn component-name [c]
  (or (some-> c .-constructor .-displayName)
      (some-> c .-constructor .-name)))

(defn comp-name []
  (if (dev?)
    (let [c *current-component*
          n (component-name c)]
      (if-not (empty? n)
        (str " (in " n ")")
        ""))
    ""))

(defn do-render [c compiler]
  (binding [*current-component* c]
    (wrap-render c compiler)))


;;; Method wrapping

(def rat-opts {:no-cache true})

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

(defn wrap-funs [fmap compiler]
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
           :render (fn render []
                     (this-as c (if util/*non-reactive*
                                  (do-render c compiler)
                                  (let [^clj rat (gobj/get c "cljsRatom")]
                                    (batch/mark-rendered c)
                                    (if (nil? rat)
                                      (ratom/run-in-reaction #(do-render c compiler) c "cljsRatom"
                                                             batch/queue-render rat-opts)
                                      (._run rat false)))))))))

(defn map-to-js [m]
  (reduce-kv (fn [o k v]
               (doto o
                 (gobj/set (name k) v)))
             #js{} m))

(defn cljsify [body compiler]
  (-> body
      camelify-map-keys
      add-obligatory
      (wrap-funs compiler)))

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
  [body compiler]
  {:pre [(map? body)]}
  (let [body (cljsify body compiler)
        methods (map-to-js (apply dissoc body :displayName :getInitialState :constructor
                                  :render :reagentRender
                                  built-in-static-method-names))
        static-methods (map-to-js (select-keys body built-in-static-method-names))
        display-name (:displayName body)
        get-initial-state (:getInitialState body)
        construct (:constructor body)
        cmp (fn [props context updater]
              (this-as ^clj this
                (.call react/Component this props context updater)
                (when construct
                  (construct this props))
                (when get-initial-state
                  (set! (.-state this) (get-initial-state this)))
                (set! (.-cljsMountOrder this) (batch/next-mount-count))
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
              (cljs.core/-write writer display-name)))
      (js/Object.defineProperty cmp "name" #js {:value display-name :writable false}))

    (set! (.-cljs$lang$type cmp) true)
    (set! (.. cmp -prototype -constructor) cmp)

    cmp))

;; Cache result to the tag but per compiler ID
;; TODO: Generate cache & get methods to the Object using macro,
;; can generate code calling interop forms.
(defn cached-react-class [compiler ^clj c]
  (gobj/get c (p/get-id compiler)))

(defn cache-react-class [compiler ^clj c constructor]
  (gobj/set c (p/get-id compiler) constructor)
  constructor)

(defn fn-to-class [compiler f]
  (assert-callable f)
  (warn-unless (not (and (react-class? f)
                         (not (reagent-class? f))))
               "Using native React classes directly in Hiccup forms "
               "is not supported. Use create-element or "
               "adapt-react-class instead: " (or (util/fun-name f)
                                                 f)
               (comp-name))
  (if (reagent-class? f)
    (cache-react-class compiler f f)
    (let [spec (meta f)
          withrender (assoc spec :reagent-render f)
          res (create-class withrender compiler)]
      (cache-react-class compiler f res))))

(defn as-class [tag compiler]
  (if-some [cached-class (cached-react-class compiler tag)]
    cached-class
    (fn-to-class compiler tag)))

(defn reactify-component [comp compiler]
  (if (react-class? comp)
    comp
    (as-class comp compiler)))

(defn functional-wrap-render
  [compiler ^clj c]
  (let [f (.-reagentRender c)
        _ (assert-callable f)
        argv (.-argv c)
        res (apply f argv)]
    (cond
      (vector? res) (p/as-element compiler res)
      (ifn? res) (let [f (if (reagent-class? res)
                           (fn [& args]
                             (p/as-element compiler (apply vector res args)))
                           res)]
                   (set! (.-reagentRender c) f)
                   (recur compiler c))
      :else res)))

(defn functional-do-render [compiler c]
  (binding [*current-component* c]
    (functional-wrap-render compiler c)))

(defn functional-render [compiler ^clj jsprops]
  (if util/*non-reactive*
    ;; Non-reactive component needs just the render fn and argv
    (functional-do-render compiler jsprops)
    (let [argv (.-argv jsprops)
          tag (.-reagentRender jsprops)

          ;; Use counter to trigger render manually.
          [_ update-count] (react/useState 0)

          ;; This object mimics React Class attributes and methods.
          ;; To support form-2 components, even the render fn needs to
          ;; be stored as it is created during the first render,
          ;; and subsequent renders need to retrieve the created fn.
          state-ref (react/useRef)

          _ (when-not (.-current state-ref)
              (let [obj #js {}]
                (set! (.-forceUpdate obj) (fn [] (update-count inc)))
                (set! (.-cljsMountOrder obj) (batch/next-mount-count))
                ;; Use reagentRender name, as that is also used
                ;; by class components, and some checks.
                ;; reagentRender is replaced with form-2 inner fn,
                ;; constructor refers to the original fn.
                (set! (.-constructor obj) tag)
                (set! (.-reagentRender obj) tag)

                (set! (.-current state-ref) obj)))

          reagent-state (.-current state-ref)

          ;; FIXME: Access cljsRatom using interop forms
          rat ^ratom/Reaction (gobj/get reagent-state "cljsRatom")]

      (react/useEffect
        (fn mount []
          (fn unmount []
            (some-> (gobj/get reagent-state "cljsRatom") ratom/dispose!)))
        ;; Ignore props - only run effect once on mount and unmount
        #js [])

      ;; Argv is also stored in the state,
      ;; so reaction fn will always see the latest value.
      (set! (.-argv reagent-state) argv)

      (batch/mark-rendered reagent-state)

      ;; static-fns :render
      (if (nil? rat)
        (ratom/run-in-reaction
          ;; Mock Class component API
          #(functional-do-render compiler reagent-state)
          reagent-state
          "cljsRatom"
          batch/queue-render
          rat-opts)
        ;; TODO: Consider passing props here, instead of keeping them in state?
        (._run rat false)))))

(defn functional-render-memo-fn
  [prev-props next-props]
  (let [old-argv (.-argv prev-props)
        new-argv (.-argv next-props)]
    (and (false? util/*always-update*)
         (try
           (= old-argv new-argv)
           (catch :default e
             (warn "Exception thrown while comparing argv's in shouldComponentUpdate: " old-argv " " new-argv " " e)
             false)))))

(defn functional-render-fn
  "Create copy of functional-render with displayName set to name of the
  original Reagent component."
  [compiler tag]
  ;; TODO: Could be disabled for optimized builds?
  ;; Or not currently - the memo wrap is required.
  (or (cached-react-class compiler tag)
      (let [f (fn [jsprops] (functional-render compiler jsprops))
            display-name (util/fun-name tag)
            _ (set! (.-displayName f) display-name)
            _ (js/Object.defineProperty f "name" #js {:value display-name :writable false})
            f (react/memo f functional-render-memo-fn)]
        (cache-react-class compiler tag f)
        f)))
