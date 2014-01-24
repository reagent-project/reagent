
(ns reagent.impl.template
  (:require [clojure.string :as string]
            [reagent.impl.reactimport :as reactimport]
            [reagent.impl.util :as util]
            [reagent.debug :refer-macros [dbg prn println]]))

(def React reactimport/React)

(def cljs-props "cljsProps")
(def cljs-children "cljsChildren")

(def isClient (not (nil? (try (.-document js/window)
                              (catch js/Object e nil)))))

(defn dash-to-camel [dashed]
  (let [words (-> dashed name (string/split #"-"))]
    (apply str (first words)
           (->> words rest (map string/capitalize)))))

(def attr-aliases {:class "className"
                   :for "htmlFor"
                   :charset "charSet"})

(defn undash-prop-name [n]
  (or (attr-aliases n)
      (dash-to-camel n)))

(def cached-prop-name (memoize undash-prop-name))
(def cached-style-name (memoize dash-to-camel))

(defn convert-prop-value [val]
  (cond (map? val) (let [obj (js-obj)]
                     (doseq [[k v] val]
                       (aset obj (cached-style-name k) (clj->js v)))
                     obj)
        (ifn? val) (fn [& args] (apply val args))
        :else (clj->js val)))

(defn set-id-class [props [id class]]
  (aset props "id" id)
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
               (doseq [[k v] props]
                 (aset objprops (cached-prop-name k)
                       (convert-prop-value v))))
             (when-not (nil? id-class)
               (set-id-class objprops id-class))
             objprops))))

(defn map-into-array [f coll]
  (let [a (into-array coll)]
    (dotimes [i (alength a)]
      (aset a i (f (aget a i))))
    a))

(declare as-component)

(defn wrapped-render [this comp id-class]
  (let [inprops (aget this "props")
        props (aget inprops cljs-props)
        hasprops (or (nil? props) (map? props))
        jsargs (->> (aget inprops cljs-children)
                    (map-into-array as-component))]
    (.unshift jsargs (convert-props props id-class))
    (.apply comp nil jsargs)))

(defn wrapped-should-update [C nextprops nextstate]
  (let [inprops (aget C "props")
        p1 (aget inprops cljs-props)
        c1 (aget inprops cljs-children)
        p2 (aget nextprops cljs-props)
        c2 (aget nextprops cljs-children)]
    (not (util/equal-args p1 c1 p2 c2))))

(defn wrap-component [comp extras name]
  (.createClass React (js-obj "render"
                              #(this-as C (wrapped-render C comp extras))
                              "shouldComponentUpdate"
                              #(this-as C (wrapped-should-update C %1 %2))
                              "displayName"
                              (or name "ComponentWrapper"))))

;; From Weavejester's Hiccup, via pump:
(def ^{:doc "Regular expression that parses a CSS-style id and class
             from a tag name."}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(def DOM (aget React "DOM"))

(defn parse-tag [tag]
  (let [[tag id class] (->> tag name (re-matches re-tag) next)
        comp (aget DOM tag)
        class' (when class
                 (string/replace class #"\." " "))]
    (assert comp (str "Unknown tag: " tag))
    [comp (when (or id class')
            [id class'])]))

(defn get-wrapper [tag]
  (let [[comp id-class] (parse-tag tag)]
    (wrap-component comp id-class (str tag))))

(def cached-wrapper (memoize get-wrapper))

(defn fn-to-class [f]
  (let [spec (meta f)
        withrender (merge spec {:render f})
        res (reagent.core/create-class withrender)
        wrapf (.-cljsReactClass res)]
    (set! (.-cljsReactClass f) wrapf)
    wrapf))

(defn as-class [tag]
  (if (keyword? tag)
    (cached-wrapper tag)
    (do
      (assert (fn? tag))
      (let [cached-class (.-cljsReactClass tag)]
        (if-not (nil? cached-class)
          cached-class
          (if (.isValidClass React tag)
            (set! (.-cljsReactClass tag) (wrap-component tag nil nil))
            (fn-to-class tag)))))))

(defn vec-to-comp [v]
  (assert (pos? (count v)))
  (let [[tag props] v
        hasmap (map? props)
        first-child (if (or hasmap (nil? props)) 2 1)
        c (as-class tag)
        jsprops (js-obj cljs-props    (if hasmap props)
                        cljs-children (if (> (count v) first-child)
                                        (subvec v first-child)))]
    (when hasmap
      (let [key (:key props)]
        (when-not (nil? key)
          (aset jsprops "key" key))))
    (c jsprops)))

(defn as-component [x]
  (cond (vector? x) (vec-to-comp x)
        (seq? x) (map-into-array as-component x)
        true x))
