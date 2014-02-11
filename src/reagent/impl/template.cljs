
(ns reagent.impl.template
  (:require [clojure.string :as string]
            [reagent.impl.reactimport :as reactimport]
            [reagent.impl.util :as util :refer [cljs-level]]
            [reagent.ratom :as ratom]
            [reagent.debug :refer-macros [dbg prn println log]]))

(def React reactimport/React)

(def debug false)
(assert (set! debug true))

(def cljs-argv "cljsArgv")

(def isClient util/isClient)

(def dont-camel-case #{"aria" "data"})

(defn hiccup-tag? [x]
  (or (keyword? x)
      (symbol? x)
      (string? x)))

(defn valid-tag? [x]
  (or (hiccup-tag? x)
      (ifn? x)))

(defn capitalize [s]
  (if (< (count s) 2)
    (string/upper-case s)
    (str (string/upper-case (subs s 0 1)) (subs s 1))))

(defn dash-to-camel [dashed]
  (if (string? dashed)
    dashed
    (let [name-str (name dashed)
          [start & parts] (string/split name-str #"-")]
      (if (dont-camel-case start)
        name-str
        (apply str start (map capitalize parts))))))

(def attr-aliases {:class "className"
                   :for "htmlFor"
                   :charset "charSet"})

(defn undash-prop-name [n]
  (or (attr-aliases n)
      (dash-to-camel n)))

(def cached-prop-name (memoize undash-prop-name))
(def cached-style-name (memoize dash-to-camel))

(defn to-js-val [v]
  (if-not (ifn? v)
    v
    (cond (keyword? v) (name v)
          (symbol? v) (str v)
          (coll? v) (clj->js v)
          :else (fn [& args] (apply v args)))))

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
  (let [is-empty (empty? props)]
    (cond
     (and is-empty (nil? id-class)) nil
     (identical? (type props) js/Object) props
     :else (let [objprops (js-obj)]
             (when-not is-empty
               (reduce-kv (fn [o k v]
                            (doto o (aset (cached-prop-name k)
                                          (convert-prop-value v))))
                          objprops props))
             (when-not (nil? id-class)
               (set-id-class objprops id-class))
             objprops))))

(defn map-into-array [f arg coll]
  (reduce (fn [a x]
            (doto a
              (.push (f x arg))))
          #js [] coll))

(declare as-component)

(def DOM (aget React "DOM"))

(def input-components #{(aget DOM "input")
                        (aget DOM "textarea")})

(defn extract-props [v]
  (let [p (get v 1)]
    (if (map? p) p)))

(defn get-props [this]
  (-> this (aget "props") (aget cljs-argv) extract-props))

(defn input-initial-state [this]
  (let [props (get-props this)]
    #js {:value (:value props)
         :checked (:checked props)}))

(defn input-handle-change [this e]
  (let [props (get-props this)
        on-change (or (props :on-change) (props "onChange"))]
    (when-not (nil? on-change)
      (let [target (.-target e)]
        (.setState this #js {:value (.-value target)
                             :checked (.-checked target)}))
      (on-change e))))

(defn input-will-receive-props [this new-props]
  (let [props (-> new-props (aget cljs-argv) extract-props)]
    (.setState this #js {:value (:value props)
                         :checked (:checked props)})))

(defn input-render-setup [this jsprops]
  (let [state (aget this "state")]
    (doto jsprops
      (aset "value" (.-value state))
      (aset "checked" (.-checked state))
      (aset "onChange" (aget this "handleChange")))))

(defn wrapped-render [this comp id-class]
  (let [inprops (aget this "props")
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
    (when (input-components comp)
      (input-render-setup this jsprops))
    (.unshift jsargs jsprops)
    (.apply comp nil jsargs)))

(defn wrapped-should-update [C nextprops nextstate]
  (let [inprops (aget C "props")
        a1 (aget inprops cljs-argv)
        a2 (aget nextprops cljs-argv)]
    (not (util/equal-args a1 a2))))

(defn wrap-component [comp extras name]
  (let [def #js {:render
                 (fn []
                   (this-as C (wrapped-render C comp extras)))
                 :shouldComponentUpdate
                 #(this-as C (wrapped-should-update C %1 %2))
                 :displayName (or name "ComponentWrapper")}]
    (when (input-components comp)
      (doto def
        (aset "shouldComponentUpdate" nil)
        (aset "getInitialState" #(this-as C (input-initial-state C)))
        (aset "handleChange" #(this-as C (input-handle-change C %)))
        (aset "componentWillReceiveProps"
              #(this-as C (input-will-receive-props C %)))))
    (.createClass React def)))

;; From Weavejester's Hiccup, via pump:
(def ^{:doc "Regular expression that parses a CSS-style id and class
             from a tag name."}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

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
        res (reagent.core/create-class withrender)
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

(defn vec-to-comp [v level]
  (assert (pos? (count v)) "Hiccup form should not be empty")
  (assert (valid-tag? (v 0))
          (str "Invalid Hiccup form: " (pr-str v)))
  (let [props (get v 1)
        c (as-class (v 0))
        jsprops (js-obj cljs-argv v
                        cljs-level level)]
    (when (map? props)
      (let [key (:key props)]
        (when-not (nil? key)
          (aset jsprops "key" key))))
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
           (seq? x) (if-not (and debug (nil? ratom/*ratom-context*))
                      (expand-seq x level)
                      (let [s (ratom/capture-derefed
                               #(expand-seq x level)
                               tmp)]
                        (when (ratom/captured tmp)
                          (warn-on-deref x))
                        s))
           true x)))
