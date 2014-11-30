
(ns reagent.impl.template
  (:require [clojure.string :as string]
            [reagent.impl.util :as util :refer [is-client]]
            [reagent.impl.component :as comp]
            [reagent.impl.batching :as batch]
            [reagent.ratom :as ratom]
            [reagent.interop :refer-macros [.' .!]]
            [reagent.debug :refer-macros [dbg prn println log dev?]]))


;; From Weavejester's Hiccup, via pump:
(def ^{:doc "Regular expression that parses a CSS-style id and class
             from a tag name."}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

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
      (util/clj-ifn? x)))

(defn to-js-val [v]
  (cond
   (string? v) v
   (number? v) v
   (keyword? v) (name v)
   (symbol? v) (str v)
   (coll? v) (clj->js v)
   (ifn? v) (fn [& args] (apply v args))
   :else v))

(defn undash-prop-name [n]
  (or (attr-aliases n)
      (util/dash-to-camel n)))


;;; Props conversion

(def cached-prop-name (util/memoize-1 undash-prop-name))
(def cached-style-name (util/memoize-1 util/dash-to-camel))

(defn convert-prop-value [x]
  (cond (string? x) x
        (number? x) x
        (map? x) (reduce-kv (fn [o k v]
                              (doto o
                                (aset (cached-prop-name k)
                                      (to-js-val v))))
                            #js{} x)
        :else (to-js-val x)))

(defn set-id-class [props [id class]]
  (let [pid (.' props :id)]
    (.! props :id (if-not (nil? pid) pid id))
    (when-not (nil? class)
      (let [old (.' props :className)]
        (.! props :className (if-not (nil? old)
                                 (str class " " old)
                                 class))))))

(defn convert-props [props id-class allow-key]
  (cond
   (and (empty? props) (nil? id-class)) nil
   (identical? (type props) js/Object) props
   :else (let [objprops
               (reduce-kv (fn [o k v]
                            (let [pname (cached-prop-name k)]
                              (if (or allow-key
                                      (not (identical? pname "key")))
                                ;; Skip key, it is set by parent
                                (aset o pname (convert-prop-value v))))
                            o)
                          #js{} props)]
           (when-not (nil? id-class)
             (set-id-class objprops id-class))
           objprops)))


;;; Specialization for input components

(defn input-unmount [this]
  (.! this :cljsInputValue nil))

(defn input-set-value [this]
  (when-some [value (.' this :cljsInputValue)]
    (.! this :cljsInputDirty false)
    (let [node (.' this getDOMNode)]
      (when (not= value (.' node :value))
        (.! node :value value)))))

(defn input-handle-change [this on-change e]
  (let [res (on-change e)]
    ;; Make sure the input is re-rendered, in case on-change
    ;; wants to keep the value unchanged
    (when-not (.' this :cljsInputDirty)
      (.! this :cljsInputDirty true)
      (batch/do-later #(input-set-value this)))
    res))

(defn input-render-setup [this jsprops]
  ;; Don't rely on React for updating "controlled inputs", since it
  ;; doesn't play well with async rendering (misses keystrokes).
  (if (and (.' jsprops hasOwnProperty "onChange")
           (.' jsprops hasOwnProperty "value"))
    (let [v (.' jsprops :value)
          value (if (nil? v) "" v)
          on-change (.' jsprops :onChange)]
      (.! this :cljsInputValue value)
      (js-delete jsprops "value")
      (doto jsprops
        (.! :defaultValue value)
        (.! :onChange #(input-handle-change this on-change %))))
    (.! this :cljsInputValue nil)))

(defn input-component? [x]
  (let [DOM (.' js/React :DOM)]
    (or (= x "input")
        (= x "textarea")
        (identical? x (.' DOM :input))
        (identical? x (.' DOM :textarea)))))


;;; Wrapping of native components

(declare make-element)

(defn wrapped-render [this comp id-class input-setup]
  (let [inprops (.' this :props)
        argv (.' inprops :argv)
        props (nth argv 1 nil)
        hasprops (or (nil? props) (map? props))
        jsprops (convert-props (if hasprops props) id-class false)]
    (when-not (nil? input-setup)
      (input-setup this jsprops))
    (make-element argv comp jsprops
                  (if hasprops 2 1))))

(defn wrapped-should-update [c nextprops nextstate]
  (or util/*always-update*
      (let [a1 (.' c :props.argv)
            a2 (.' nextprops :argv)]
        (not (util/equal-args a1 a2)))))

(defn add-input-methods [spec]
  (doto spec
    (.! :componentDidUpdate #(this-as c (input-set-value c)))
    (.! :componentWillUnmount #(this-as c (input-unmount c)))))

(defn wrap-component [comp extras name]
  (let [input? (input-component? comp)
        input-setup (if input? input-render-setup)
        spec #js{:render
                 #(this-as C (wrapped-render C comp extras input-setup))
                 :shouldComponentUpdate
                 #(this-as C (wrapped-should-update C %1 %2))
                 :displayName (or name "ComponentWrapper")}]
    (when input?
      (add-input-methods spec))
    (.' js/React createClass spec)))


;;; Conversion from Hiccup forms

(defn parse-tag [hiccup-tag]
  (let [[tag id class] (->> hiccup-tag name (re-matches re-tag) next)
        class' (when class
                 (string/replace class #"\." " "))]
    (assert tag (str "Unknown tag: '" hiccup-tag "'"))
    [tag (when (or id class')
           [id class'])]))

(defn get-wrapper [tag]
  (let [[comp id-class] (parse-tag tag)]
    (wrap-component comp id-class (str tag))))

(def cached-wrapper (util/memoize-1 get-wrapper))

(defn fn-to-class [f]
  (assert (ifn? f) (str "Expected a function, not " (pr-str f)))
  (let [spec (meta f)
        withrender (assoc spec :component-function f)
        res (comp/create-class withrender)
        wrapf (util/cached-react-class res)]
    (util/cache-react-class f wrapf)
    wrapf))

(defn as-class [tag]
  (if (hiccup-tag? tag)
    (cached-wrapper tag)
    (let [cached-class (util/cached-react-class tag)]
      (if-not (nil? cached-class)
        cached-class
        (fn-to-class tag)))))

(def cached-parse (util/memoize-1 parse-tag))

(defn native-element [tag argv]
  (when (hiccup-tag? tag)
    (let [[comp id-class] (cached-parse tag)]
      (when-not (input-component? comp)
        (let [props (nth argv 1 nil)
              hasprops (or (nil? props) (map? props))
              jsprops (convert-props (if hasprops props) id-class true)]
          ;; TODO: Meta key
          (make-element argv comp jsprops
                        (if hasprops 2 1)))))))

(defn get-key [x]
  (when (map? x) (get x :key)))

(defn vec-to-comp [v]
  (assert (pos? (count v)) "Hiccup form should not be empty")
  (let [tag (nth v 0)]
    (assert (valid-tag? tag)
            (str "Invalid Hiccup form: " (pr-str v)))
    (let [ne (native-element tag v)]
      (if (some? ne)
        ne
        (let [c (as-class tag)
              jsprops #js{:argv v}]
          (let [k (-> v meta get-key)
                k' (if (nil? k)
                     (-> v (nth 1 nil) get-key)
                     k)]
            (when-not (nil? k')
              (.! jsprops :key k')))
          (.' js/React createElement c jsprops))))))

(def seq-ctx #js{})

(defn warn-on-deref [x]
  (when-not (.' seq-ctx :warned)
    (log "Warning: Reactive deref not supported in seq in "
         (pr-str x))
    (.! seq-ctx :warned true)))

(declare expand-seq)

(defn as-component
  [x]
  (cond (string? x) x
        (vector? x) (vec-to-comp x)
        (seq? x) (if (dev?)
                   (if (nil? ratom/*ratom-context*)
                     (expand-seq x)
                     (let [s (ratom/capture-derefed
                              #(expand-seq x)
                              seq-ctx)]
                       (when (ratom/captured seq-ctx)
                         (warn-on-deref x))
                       s))
                   (expand-seq x))
        true x))

;; Cheat, to avoid ugly circular dependency
(set! reagent.impl.component/as-component as-component)

(defn expand-seq [s]
  (let [a (into-array s)]
    (dotimes [i (alength a)]
      (aset a i (as-component (aget a i))))
    a))

(defn make-element [argv comp jsprops first-child]
  (if (== (count argv) (inc first-child))
    ;; Optimize common case of one child
    (.' js/React createElement comp jsprops
        (as-component (nth argv first-child)))
    (.apply (.' js/React :createElement) nil
            (reduce-kv (fn [a k v]
                         (when (>= k first-child)
                           (.push a (as-component v)))
                         a)
                       #js[comp jsprops] argv))))
