
(ns cloact.impl.component
  (:require-macros [cloact.ratom :refer [reaction]]
                   [cloact.debug :refer [dbg prn]])
  (:require [cloact.impl.template :as tmpl]
            [cloact.impl.util :as util]
            [cloact.ratom :as ratom]))

(def React tmpl/React)

;;; Atom protocol as mixin

(def CloactMixin (js-obj))
(def -ToExtend (js-obj))
(set! (.-prototype -ToExtend) CloactMixin)

(declare get-props)
(declare get-children)

(extend-type -ToExtend
  IEquiv
  (-equiv [C other] (identical? C other))

  IDeref
  (-deref [C] (.-state C))

  IMeta
  (-meta [C] nil)

  IPrintWithWriter
  (-pr-writer [C writer opts]
    (-write writer (str "#<" (-> C .-constructor .-displayName) ": "))
    (pr-writer (.-state C) writer opts)
    (-write writer ">"))

  IWatchable
  (-notify-watches [C old new]
    (.replaceState C new))
  (-add-watch [C key f] (assert false "Component isn't really watchable"))
  (-remove-watch [C key] (assert false "Component isn't really watchable"))

  ILookup
  (-lookup [C key]
    (-lookup C key nil))
  (-lookup [C key not-found]
    (case key
      :props (get-props C)
      :children (get-children C)
      :dom-node (.getDOMNode C)
      :refs (.-refs C)
      not-found))

  IHash
  (-hash [C] (goog/getUid C)))

(doseq [x (js-keys CloactMixin)]
  ;; Tell React to not autobind
  (aset (aget CloactMixin x) "__reactDontBind" true))

;; Reference -ToExtend to show fucking google closure that it is used
(when-not -ToExtend
  (.log js/console "this should never happen to " -ToExtend))

;;; Function wrapping

(defn- args-of [C]
  (-> C .-props .-cljsArgs))

(defn- cljs-props [C]
  (let [args (args-of C)
        p (nth args 1 nil)]
    (when (map? p)
      p)))

(defn- first-child [args]
  (let [p? (nth args 1 nil)]
    (if (or (nil? p?) (map? p?)) 2 1)))

(defn- get-children [C]
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
  (let [ctx ratom/*ratom-context*]
    (if (or (nil? ctx) (.-isRenderContext ctx))
      (cljs-props C)
      ;; Use atom if getting props in an ratom
      (deref (or (.-cljsPropsAtom C)
                 (set! (.-cljsPropsAtom C) (ratom/ratom (cljs-props C))))))))

(defn- do-render [C f]
  (set! (.-isRenderContext ratom/*ratom-context*) true)
  (let [res (f (cljs-props C) f @C)
        conv (if (vector? res)
               (tmpl/as-component res)
               (if (fn? res)
                 (do-render C (set! (.-cljsRenderFn C) res))
                 res))]
    conv))

(defn- render [C]
  (assert C)
  (when (nil? (.-cljsRatom C))
    (set! (.-cljsRatom C)
          (reaction :auto-run #(.forceUpdate C)
                    (do-render C (.-cljsRenderFn C)))))
  (ratom/run (.-cljsRatom C)))

(defn- custom-wrapper [key f]
  (case key
    :getDefaultProps
    (assert false "getDefaultProps not supported yet")
    
    :getInitialState
    (fn [C]
      ;; reset! doesn't call -notifyWatches unless -watches is set
      (set! (.-watches C) {})
      (when f
        (set! (.-cljsOldState C)
              (merge (.-state C) (f C)))))

    :componentWillReceiveProps
    (fn [C props]
      (when-not (nil? (.-cljsPropsAtom C))
        (reset! (.-cljsPropsAtom C) (cljs-props C)))
      (when f (f C props)))

    :shouldComponentUpdate
    (fn [C nextprops nextstate]
      (assert (nil? f) "shouldComponentUpdate is not yet supported")
      (assert (vector? (-> C .-props .-cljsArgs)))
      (let [a1 (-> C .-props .-cljsArgs)
            a2 (-> nextprops .-cljsArgs)
            ostate (.-cljsOldState C)
            eq (and (tmpl/equal-args a1 a2)
                    (= ostate nextstate))]
        (set! (.-cljsOldState C) nextstate)
        (not eq)))

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

(defn- default-wrapper [f]
  (if (fn? f)
    (fn [& args]
      (this-as C (apply f C args)))
    f))

(defn- get-wrapper [key f name]
  ;; (when (and name (fn? f) (nil? (aget f "displayName")))
  ;;   (aset f "displayName" (str name key)))
  (let [wrap (custom-wrapper key f)]
    (when (and wrap f)
      (assert (fn? f)
              (str "Expected function in " name key " but got " f)))
    (default-wrapper (or wrap f))))

(def obligatory {:getInitialState nil
                 :componentWillReceiveProps nil
                 :shouldComponentUpdate nil
                 :componentWillUnmount nil})

(def aliases {:initialState :getInitialState
              :defaultProps :getDefaultProps})

(defn- camelify-map-keys [m]
  (into {} (for [[k v] m]
             [(-> k tmpl/dash-to-camel keyword) v])))

(defn- allow-aliases [m]
  (into {} (for [[k v] m]
             [(get aliases k k) v])))

(defn- add-obligatory [fun-map]
  (merge obligatory fun-map))

(defn- wrap-funs [fun-map]
  (let [name (or (:displayName fun-map)
                 (when-let [r (:render fun-map)]
                   (or (.-displayName r)
                       (.-name r))))
        name1 (if (empty? name) (str (gensym "cloact")) name)]
    (into {} (for [[k v] (assoc fun-map :displayName name1)]
               [k (get-wrapper k v name)]))))

(defn- add-atom-mixin
  [props-map]
  (merge-with concat props-map {:mixins [CloactMixin]}))

(defn- cljsify [body]
  (-> body
      camelify-map-keys
      allow-aliases
      add-obligatory
      wrap-funs
      add-atom-mixin
      clj->js))

(defn create-class
  [body]
  (let [spec (cljsify body)
        res (.createClass React spec)
        f (fn [& args]
            (let [arg (js-obj)]
              (set! (.-cljsArgs arg) (apply vector res args))
              (res arg)))]
    (set! (.-cljsReactClass f) res)
    (set! (.-cljsReactClass res) res)
    f))
