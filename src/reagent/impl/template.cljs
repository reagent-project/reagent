
(ns reagent.impl.template
  (:require [clojure.string :as string]
            [reagent.impl.util :as util
             :refer [cljs-level cljs-argv is-client React]]
            [reagent.impl.component :as comp]
            [reagent.ratom :as ratom]
            [reagent.debug :refer-macros [dbg prn println log dev?]]))

;; From Weavejester's Hiccup, via pump:
(def ^{:doc "Regular expression that parses a CSS-style id and class
             from a tag name."}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(def DOM (aget React "DOM"))

(def attr-aliases {:class "className"
                   :for "htmlFor"
                   :charset "charSet"})


;;; Common utilities

(defn hiccup-tag? [x]
  (or (keyword? x)
      (symbol? x)
      (string? x)))

(defn valid-tag? [x]
  (or (hiccup-tag? x)
      (ifn? x)))

(defn map-into-array [f arg coll]
  (let [a (into-array coll)]
    (dotimes [i (alength a)]
      (aset a i (f (aget a i) arg)))
    a))

(defn to-js-val [v]
  (if-not (ifn? v)
    v
    (cond (keyword? v) (name v)
          (symbol? v) (str v)
          (coll? v) (clj->js v)
          :else (fn [& args] (apply v args)))))

(defn undash-prop-name [n]
  (or (attr-aliases n)
      (util/dash-to-camel n)))


;;; Props conversion

(def cached-prop-name (memoize undash-prop-name))
(def cached-style-name (memoize util/dash-to-camel))

(defn convert-prop-value [val]
  (if (map? val)
    (reduce-kv (fn [res k v]
                 (doto res
                   (aset (cached-prop-name k)
                         (to-js-val v))))
               (js-obj) val)
    (to-js-val val)))

(defn set-id-class [props [id class]]
  (aset props "id" (or (aget props "id") id))
  (when class
    (aset props "className" (if-let [old (aget props "className")]
                              (str class " " old)
                              class))))

(defn convert-props [props id-class]
  (cond
   (and (empty? props) (nil? id-class)) nil
   (identical? (type props) js/Object) props
   :else (let [objprops
               (reduce-kv (fn [o k v]
                            (let [pname (cached-prop-name k)]
                              (if-not (identical? pname "key")
                                ;; Skip key, it is set by parent
                                (aset o pname (convert-prop-value v))))
                            o)
                          #js {} props)]
           (when-not (nil? id-class)
             (set-id-class objprops id-class))
           objprops)))


;;; Specialization for input components

(defn input-initial-state [this]
  (let [props (util/get-props this)]
    #js {:value (:value props)
         :checked (:checked props)}))

(defn input-handle-change [this e]
  (let [props (util/get-props this)
        on-change (or (props :on-change) (props "onChange"))]
    (when-not (nil? on-change)
      (let [target (.-target e)]
        (.setState this #js {:value (.-value target)
                             :checked (.-checked target)}))
      (on-change e))))

(defn input-will-receive-props [this new-props]
  (let [props (-> new-props (aget cljs-argv) util/extract-props)]
    (.setState this #js {:value (:value props)
                         :checked (:checked props)})))

(defn input-render-setup [this jsprops]
  (let [state (aget this "state")]
    (doto jsprops
      (aset "value" (.-value state))
      (aset "checked" (.-checked state))
      (aset "onChange" (aget this "handleChange")))))

(def input-components #{(aget DOM "input")
                        (aget DOM "textarea")})


;;; Wrapping of native components

(declare as-component)

(defn wrapped-render [this comp id-class input-setup]
  (let [inprops (util/js-props this)
        argv (aget inprops cljs-argv)
        props (get argv 1)
        hasprops (or (nil? props) (map? props))
        first-child (if hasprops 2 1)
        children (if (> (count argv) first-child)
                   (subvec argv first-child))
        jsargs (map-into-array as-component
                               (inc (aget inprops cljs-level))
                               children)
        jsprops (convert-props (if hasprops props) id-class)]
    (when-not (nil? input-setup)
      (input-setup this jsprops))
    (.unshift jsargs jsprops)
    (.apply comp nil jsargs)))

(defn wrapped-should-update [C nextprops nextstate]
  (let [inprops (util/js-props C)
        a1 (aget inprops cljs-argv)
        a2 (aget nextprops cljs-argv)]
    (not (util/equal-args a1 a2))))

(defn add-input-methods [spec]
  (doto spec
    (aset "shouldComponentUpdate" nil)
    (aset "getInitialState" #(this-as C (input-initial-state C)))
    (aset "handleChange" #(this-as C (input-handle-change C %)))
    (aset "componentWillReceiveProps"
          #(this-as C (input-will-receive-props C %)))))

(defn wrap-component [comp extras name]
  (let [input? (input-components comp)
        input-setup (if input? input-render-setup)
        spec #js {:render
                 #(this-as C (wrapped-render C comp extras input-setup))
                 :shouldComponentUpdate
                 #(this-as C (wrapped-should-update C %1 %2))
                 :displayName (or name "ComponentWrapper")}]
    (when input?
      (add-input-methods spec))
    (.createClass React spec)))

(defn create-class [spec]
  (comp/create-class spec as-component))


;;; Conversion from Hiccup forms

(defn parse-tag [hiccup-tag]
  (let [[tag id class] (->> hiccup-tag name (re-matches re-tag) next)
        comp (aget DOM tag)
        class' (when class
                 (string/replace class #"\." " "))]
    (assert comp (str "Unknown tag: '" hiccup-tag "'"))
    [comp (when (or id class')
            [id class'])]))

(defn get-wrapper [tag]
  (let [[comp id-class] (parse-tag tag)]
    (wrap-component comp id-class (str tag))))

(def cached-wrapper (memoize get-wrapper))

(defn fn-to-class [f]
  (let [spec (meta f)
        withrender (assoc spec :component-function f)
        res (create-class withrender)
        wrapf (.-cljsReactClass res)]
    (set! (.-cljsReactClass f) wrapf)
    wrapf))

(defn as-class [tag]
  (if (hiccup-tag? tag)
    (cached-wrapper tag)
    (do
      (let [cached-class (.-cljsReactClass tag)]
        (if-not (nil? cached-class)
          cached-class
          (if (.isValidClass React tag)
            (set! (.-cljsReactClass tag) (wrap-component tag nil nil))
            (fn-to-class tag)))))))

(defn get-key [x]
  (when (map? x) (get x :key)))

(defn vec-to-comp [v level]
  (assert (pos? (count v)) "Hiccup form should not be empty")
  (assert (valid-tag? (v 0))
          (str "Invalid Hiccup form: " (pr-str v)))
  (let [c (as-class (v 0))
        jsprops (js-obj cljs-argv v
                        cljs-level level)]
    (let [k (-> v meta get-key)
          k' (if (nil? k)
               (-> v (get 1) get-key)
               k)]
      (when-not (nil? k')
        (aset jsprops "key" k')))
    (c jsprops)))

(def tmp #js {})

(defn warn-on-deref [x]
  (when-not (.-warned tmp)
    (log "Warning: Reactive deref not supported in seq in "
         (pr-str x))
    (set! (.-warned tmp) true)))

(defn expand-seq [x level]
  (map-into-array as-component (inc level) x))

(defn as-component
  ([x] (as-component x 0))
  ([x level]
     (cond (vector? x) (vec-to-comp x level)
           (seq? x) (if-not (and (dev?) (nil? ratom/*ratom-context*))
                      (expand-seq x level)
                      (let [s (ratom/capture-derefed
                               #(expand-seq x level)
                               tmp)]
                        (when (ratom/captured tmp)
                          (warn-on-deref x))
                        s))
           true x)))
