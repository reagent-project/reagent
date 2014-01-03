
(ns cloact.impl.template
  (:require-macros [cloact.debug :refer [dbg prn println]])
  (:require [clojure.string :as string]
            [cloact.impl.reactimport :as reacts]))

(def React reacts/React)

(defn dash-to-camel [dashed]
  (let [words (string/split (name dashed) #"-")
        camels (map string/capitalize (rest words))]
    (apply str (first words) camels)))

;; From Weavejester's Hiccup, via pump:
;; https://github.com/weavejester/hiccup/blob/master/src/hiccup/compiler.clj#L32
(def ^{:doc "Regular expression that parses a CSS-style id and class
             from a tag name."
       :private true}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(def DOM (aget React "DOM"))

(defn parse-tag [tag]
  (let [[tag id class] (->> tag name (re-matches re-tag) next)
        comp (aget DOM tag)
        class' (when class
                 (string/replace class #"\." " "))]
    [comp (when (or id class')
            [id class'])]))

(def attr-aliases {"class" "className"
                   "for" "htmlFor"})

(defn undash-prop-name [n]
  (let [undashed (dash-to-camel n)]
    (get attr-aliases undashed undashed)))

(def cached-prop-name (memoize undash-prop-name))
(def cached-style-name (memoize dash-to-camel))

(defn convert-prop-value [val]
  (cond (map? val) (let [obj (js-obj)]
                     (doseq [[k v] val]
                       (aset obj (cached-style-name k) (clj->js v)))
                     obj)
        (ifn? val) (fn [& args] (apply val args))
        :else (clj->js val)))

(defn set-tag-extra [props [id class]]
  (set! (.-id props) id)
  (when class
    (set! (.-className props)
          (if-let [old (.-className props)]
            (str class " " old)
            class))))

(defn convert-props [props extra]
  (let [is-empty (empty? props)]
    (cond
     (and is-empty (nil? extra)) nil
     (identical? (type props) js/Object) props
     :else (let [objprops (js-obj)]
             (when-not is-empty
               (doseq [[k v] props]
                 (aset objprops (cached-prop-name k)
                       (convert-prop-value v))))
             (when-not (nil? extra)
               (set-tag-extra objprops extra))
             objprops))))

(defn identical-parts [v1 v2 from]
  ;; Compare two vectors, from item with index "from", using identical?
  (let [end (count v1)]
    (loop [n from]
      (if (>= n end)
        true
        (if (identical? (nth v1 n) (nth v2 n))
          (recur (inc n))
          false)))))

(def -not-found (js-obj))

(defn shallow-equal-maps [x y]
  ;; Compare two maps, using identical? on all values
  (or (identical? x y)
      (when (== (count x) (count y))
        (reduce-kv (fn [res k v]
                     (let [yv (get y k -not-found)]
                       (if (or (identical? v yv)
                               ;; hack to allow cloact.core/partial
                               (and (ifn? v) (= v yv)))
                         res
                         (reduced false))))
                   true x))))

(defn equal-args [v1 v2]
  ;; Compare two "args" vectors, i.e things like [:div {:foo "bar} "baz"],
  ;; using identical? on all individual parts.
  (or (identical? v1 v2)
      (let [c1 (count v1)]
        (and (= (nth v1 0) (nth v2 0)) ; may be symbol or fn
             (identical? c1 (count v2))
             (if (< c1 2)
               true
               (let [props1 (nth v1 1)]
                 (if (or (nil? props1) (map? props1))
                   (and (identical-parts v1 v2 2)
                        (shallow-equal-maps props1 (nth v2 1)))
                   (identical-parts v1 v2 1))))))))

(declare wrapper)

(defn fn-to-class [f]
  (let [spec (meta f)
        withrender (merge spec {:render f})
        res (cloact.core/create-class withrender)]
    (set! (.-cljsReactClass f) (.-cljsReactClass res))
    res))

(defn as-class [x]
  (cond
   (keyword? x) wrapper
   (not (nil? (.-cljsReactClass x))) x
   :else (do (assert (fn? x))
             (if (.isValidClass React x)
               wrapper
               (fn-to-class x)))))

(defn vec-to-comp [v]
  (let [[tag props] v
        c (.-cljsReactClass (as-class tag))
        obj (js-obj)]
    (set! (.-cljsArgs obj) v)
    (when (map? props)
      (let [key (:key props)]
        (when-not (nil? key)
          (set! (.-key obj) key))))
    (c obj)))

(defn map-into-array [f coll]
  (let [a (into-array coll)
        len (alength a)]
    (dotimes [i len]
      (aset a i (f (aget a i))))
    a))

(defn as-component [x]
  (cond (vector? x) (vec-to-comp x)
        (seq? x) (map-into-array as-component x)
        true x))

(def cached-tag (memoize parse-tag))

(defn render-wrapped [this]
  (let [inprops (aget this "props")
        args (.-cljsArgs inprops)
        [tag scnd] args
        hasprops (or (nil? scnd) (map? scnd))
        [native extra] (when (keyword? tag) (cached-tag tag))
        f (or native tag)
        jsprops (convert-props (when hasprops scnd) extra)
        jsargs (->> args
                    (drop (if hasprops 2 1))
                    (map-into-array as-component))]
    (assert (.isValidClass React f))
    (assert (nil? (.-cljsReactClass f)))
    (.apply f nil (.concat (array jsprops) jsargs))))

(defn should-update-wrapped [C nextprops nextstate]
  (let [a1 (-> C (aget "props") .-cljsArgs)
        a2 (-> nextprops .-cljsArgs)]
    (not (equal-args a1 a2))))

(def wrapper
  (.createClass React (js-obj "render"
                              #(this-as C (render-wrapped C))
                              "shouldComponentUpdate"
                              #(this-as C (should-update-wrapped C %1 %2)))))

(set! (.-cljsReactClass wrapper) wrapper)
