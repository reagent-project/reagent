(ns reagent.impl.template
  (:require [clojure.string :as string]
            [reagent.impl.util :as util :refer [is-client]]
            [reagent.impl.component :as comp]
            [reagent.impl.batching :as batch]
            [reagent.ratom :as ratom]
            [reagent.interop :refer-macros [.' .!]]
            [reagent.debug :refer-macros [dbg prn println log dev?
                                          warn warn-unless]]))

;; From Weavejester's Hiccup, via pump:
(def ^{:doc "Regular expression that parses a CSS-style id and class
             from a tag name."}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(deftype NativeWrapper [comp])


;;; Common utilities

(defn named? [x]
  (or (keyword? x)
      (symbol? x)))

(defn hiccup-tag? [x]
  (or (named? x)
      (string? x)))

(defn valid-tag? [x]
  (or (hiccup-tag? x)
      (ifn? x)
      (instance? NativeWrapper x)))


;;; Props conversion

(def prop-name-cache #js{:class "className"
                         :for "htmlFor"
                         :charset "charSet"})

(defn obj-get [o k]
  (when (.hasOwnProperty o k)
    (aget o k)))

(defn cached-prop-name [k]
  (if (named? k)
    (if-some [k' (obj-get prop-name-cache (name k))]
      k'
      (aset prop-name-cache (name k)
            (util/dash-to-camel k)))
    k))

(defn convert-prop-value [x]
  (cond (or (string? x) (number? x) (fn? x)) x
        (named? x) (name x)
        (map? x) (reduce-kv (fn [o k v]
                              (doto o
                                (aset (cached-prop-name k)
                                      (convert-prop-value v))))
                            #js{} x)
        (coll? x) (clj->js x)
        (ifn? x) (fn [& args] (apply x args))
        true (clj->js x)))

(defn set-id-class [props id class]
  (let [p (if (nil? props) #js{} props)]
    (when (and (some? id) (nil? (.' p :id)))
      (.! p :id id))
    (when (some? class)
      (let [old (.' p :className)]
        (.! p :className (if (some? old)
                           (str class " " old)
                           class))))
    p))

(defn convert-props [props id-class]
  (let [id (.' id-class :id)
        class (.' id-class :className)
        no-id-class (and (nil? id) (nil? class))]
    (if (and no-id-class (empty? props))
      nil
      (let [objprops (convert-prop-value props)]
        (if no-id-class
          objprops
          (set-id-class objprops id class))))))


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
  (or (identical? x "input")
      (identical? x "textarea")))

(def reagent-input-class nil)

(declare make-element)

(def input-spec
  {:display-name "ReagentInput"
   :component-did-update input-set-value
   :component-will-unmount input-unmount
   :reagent-render
   (fn [argv comp jsprops first-child]
     (let [this comp/*current-component*]
       (input-render-setup this jsprops)
       (make-element argv comp jsprops first-child)))})

(defn reagent-input [argv comp jsprops first-child]
  (when (nil? reagent-input-class)
    (set! reagent-input-class
          (comp/create-class input-spec)))
  (reagent-input-class argv comp jsprops first-child))


;;; Conversion from Hiccup forms

(defn parse-tag [hiccup-tag]
  (let [[tag id class] (->> hiccup-tag name (re-matches re-tag) next)
        class' (when class
                 (string/replace class #"\." " "))]
    (assert tag (str "Invalid tag: '" hiccup-tag "'"
                     (comp/comp-name)))
    #js{:name tag
        :id id
        :className class'}))

(defn fn-to-class [f]
  (assert (ifn? f) (str "Expected a function, not " (pr-str f)))
  (warn-unless (not (and (fn? f)
                         (some? (.' f :type))))
               "Using native React classes directly in Hiccup forms "
               "is not supported. Use create-element or "
               "adapt-react-class instead: " (.' f :type)
               (comp/comp-name))
  (let [spec (meta f)
        withrender (assoc spec :reagent-render f)
        res (comp/create-class withrender)
        wrapf (util/cached-react-class res)]
    (util/cache-react-class f wrapf)
    wrapf))

(defn as-class [tag]
  (if-some [cached-class (util/cached-react-class tag)]
    cached-class
    (fn-to-class tag)))

(defn get-key [x]
  (when (map? x) (get x :key)))

(defn key-from-vec [v]
  (if-some [k (some-> (meta v) get-key)]
    k
    (-> v (nth 1 nil) get-key)))

(defn reag-element [tag v]
  (let [c (as-class tag)
        jsprops #js{:argv v}]
    (some->> v key-from-vec (.! jsprops :key))
    (.' js/React createElement c jsprops)))

(defn adapt-react-class [c]
  (NativeWrapper. #js{:name c
                      :id nil
                      :class nil}))

(def tag-name-cache #js{})

(defn cached-parse [x]
  (if (hiccup-tag? x)
    (if-some [s (obj-get tag-name-cache (name x))]
      s
      (aset tag-name-cache (name x) (parse-tag x)))
    (when (instance? NativeWrapper x)
      (.-comp x))))

(declare as-element)

(defn native-element [tag argv]
  (when-let [parsed (cached-parse tag)]
    (let [comp (.' parsed :name)]
      (let [props (nth argv 1 nil)
            hasprops (or (nil? props) (map? props))
            jsprops (convert-props (if hasprops props) parsed)
            first-child (if hasprops 2 1)]
        (if (input-component? comp)
          (-> [reagent-input argv comp jsprops first-child]
              (with-meta (meta argv))
              as-element)
          (let [p (if-some [key (some-> (meta argv) get-key)]
                    (doto (if (nil? jsprops) #js{} jsprops)
                      (.! :key key))
                    jsprops)]
            (make-element argv comp p first-child)))))))

(defn vec-to-elem [v]
  (assert (pos? (count v))
          (str "Hiccup form should not be empty: "
               (pr-str v) (comp/comp-name)))
  (let [tag (nth v 0)]
    (assert (valid-tag? tag)
            (str "Invalid Hiccup form: "
                 (pr-str v) (comp/comp-name)))
    (if-some [ne (native-element tag v)]
      ne
      (reag-element tag v))))

(declare expand-seq)
(declare expand-seq-check)

(defn as-element [x]
  (cond (string? x) x
        (vector? x) (vec-to-elem x)
        (seq? x) (if (dev?)
                   (expand-seq-check x)
                   (expand-seq x))
        true x))

(defn expand-seq [s]
  (let [a (into-array s)]
    (dotimes [i (alength a)]
      (aset a i (as-element (aget a i))))
    a))

(defn expand-seq-dev [s o]
  (let [a (into-array s)]
    (dotimes [i (alength a)]
      (let [val (aget a i)]
        (when (and (vector? val)
                   (nil? (key-from-vec val)))
          (.! o :no-key true))
        (aset a i (as-element val))))
    a))

(defn expand-seq-check [x]
  (let [ctx #js{}
        res (if (nil? ratom/*ratom-context*)
              (expand-seq-dev x ctx)
              (ratom/capture-derefed #(expand-seq-dev x ctx)
                                     ctx))]
    (when (ratom/captured ctx)
      (warn "Reactive deref not supported in lazy seq, "
            "it should be wrapped in doall"
            (comp/comp-name) ". Value:\n" (pr-str x)))
    (when (.' ctx :no-key)
      (warn "Every element in a seq should have a unique "
            ":key" (comp/comp-name) ". Value: " (pr-str x)))
    res))

(defn make-element [argv comp jsprops first-child]
  (case (- (count argv) first-child)
    ;; Optimize cases of zero or one child
    0 (.' js/React createElement comp jsprops)

    1 (.' js/React createElement comp jsprops
          (as-element (nth argv first-child)))

    (.apply (.' js/React :createElement) nil
            (reduce-kv (fn [a k v]
                         (when (>= k first-child)
                           (.push a (as-element v)))
                         a)
                       #js[comp jsprops] argv))))
