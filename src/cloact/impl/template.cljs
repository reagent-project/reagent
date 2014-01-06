
(ns cloact.impl.template
  (:require-macros [cloact.debug :refer [dbg prn println]])
  (:require [clojure.string :as string]
            [cloact.impl.reactimport :as reacts]
            [cloact.impl.util :as util]))

(def React reacts/React)

(def isClient (not (nil? (try (.-document js/window)
                              (catch js/Object e nil)))))

(defn dash-to-camel [dashed]
  (let [words (string/split (name dashed) #"-")
        camels (map string/capitalize (rest words))]
    (apply str (first words) camels)))

(def attr-aliases {"class" "className"
                   "for" "htmlFor"
                   "charset" "charSet"})

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

(defn map-into-array [f coll]
  (let [a (into-array coll)
        len (alength a)]
    (dotimes [i len]
      (aset a i (f (aget a i))))
    a))

(declare as-component)

(defn wrapped-render [this comp extra]
  (let [inprops (aget this "props")
        args (.-cljsArgs inprops)
        [_ scnd] args
        hasprops (or (nil? scnd) (map? scnd))
        jsprops (convert-props (if hasprops scnd) extra)
        jsargs (->> args
                    (drop (if hasprops 2 1))
                    (map-into-array as-component))]
    (.apply comp nil (.concat (array jsprops) jsargs))))

(defn wrapped-should-update [C nextprops nextstate]
  (let [a1 (-> C (aget "props") .-cljsArgs)
        a2 (-> nextprops .-cljsArgs)]
    (not (util/equal-args a1 a2))))

(defn wrap-component [comp extras]
  (let [spec #js {:render #(this-as C (wrapped-render C comp extras))
                  :shouldComponentUpdate
                  #(this-as C (wrapped-should-update C %1 %2))}]
    (.createClass React spec)))

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

(defn get-wrapper [tag]
  (let [[comp extra] (parse-tag tag)]
    (wrap-component comp extra)))

(def cached-wrapper (memoize get-wrapper))

(defn fn-to-class [f]
  (assert (fn? f))
  (let [spec (meta f)
        withrender (merge spec {:render f})
        res (cloact.core/create-class withrender)
        wrapf (.-cljsReactClass res)]
    (set! (.-cljsReactClass f) wrapf)
    wrapf))

(defn as-class [x]
  (cond
   (keyword? x) (cached-wrapper x)
   (not (nil? (.-cljsReactClass x))) (.-cljsReactClass x)
   :else (do (assert (fn? x))
             (if (.isValidClass React x)
               (set! (.-cljsReactClass x) (wrap-component x nil))
               (fn-to-class x)))))

(defn vec-to-comp [v]
  (assert (pos? (count v)))
  (let [[tag props] v
        c (as-class tag)
        obj (js-obj)]
    (set! (.-cljsArgs obj) v)
    (when (map? props)
      (let [key (:key props)]
        (when-not (nil? key)
          (set! (.-key obj) key))))
    (c obj)))

(defn as-component [x]
  (cond (vector? x) (vec-to-comp x)
        (seq? x) (map-into-array as-component x)
        true x))
