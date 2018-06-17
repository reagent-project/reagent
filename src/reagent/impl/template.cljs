(ns reagent.impl.template
  (:require [react :as react]
            [clojure.string :as string]
            [clojure.walk :refer [prewalk]]
            [reagent.impl.util :as util :refer [is-client]]
            [reagent.impl.component :as comp]
            [reagent.impl.batching :as batch]
            [reagent.ratom :as ratom]
            [reagent.interop :refer-macros [$ $!]]
            [reagent.debug :refer-macros [dbg prn println log dev?
                                          warn warn-unless]]))

(declare as-element)

;; From Weavejester's Hiccup, via pump:
(def ^{:doc "Regular expression that parses a CSS-style id and class
             from a tag name."}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(deftype NativeWrapper [])


;;; Common utilities

(defn ^boolean named? [x]
  (or (keyword? x)
      (symbol? x)))

(defn ^boolean hiccup-tag? [x]
  (or (named? x)
      (string? x)))

(defn ^boolean valid-tag? [x]
  (or (hiccup-tag? x)
      (ifn? x)
      (instance? NativeWrapper x)))


;;; Props conversion

(def prop-name-cache #js{:class "className"
                         :for "htmlFor"
                         :charset "charSet"})

(defn cache-get [o k]
  (when ^boolean (.hasOwnProperty o k)
    (aget o k)))

(defn cached-prop-name [k]
  (if (named? k)
    (if-some [k' (cache-get prop-name-cache (name k))]
      k'
      (aset prop-name-cache (name k)
            (util/dash-to-camel k)))
    k))

(defn ^boolean js-val? [x]
  (not (identical? "object" (goog/typeOf x))))

(declare convert-prop-value)

(defn kv-conv [o k v]
  (doto o
    (aset (cached-prop-name k)
          (convert-prop-value v))))

(defn convert-prop-value [x]
  (cond (js-val? x) x
        (named? x) (name x)
        (map? x) (reduce-kv kv-conv #js{} x)
        (coll? x) (clj->js x)
        (ifn? x) (fn [& args]
                   (apply x args))
        :else (clj->js x)))

;; Previous few functions copied for custom elements,
;; without mapping from class to className etc.

(def custom-prop-name-cache #js{})

(defn cached-custom-prop-name [k]
  (if (named? k)
    (if-some [k' (cache-get custom-prop-name-cache (name k))]
      k'
      (aset custom-prop-name-cache (name k)
            (util/dash-to-camel k)))
    k))

(defn custom-kv-conv [o k v]
  (doto o
    (aset (cached-custom-prop-name k)
          (convert-prop-value v))))

(defn convert-custom-prop-value [x]
  (cond (js-val? x) x
        (named? x) (name x)
        (map? x) (reduce-kv custom-kv-conv #js{} x)
        (coll? x) (clj->js x)
        (ifn? x) (fn [& args]
                   (apply x args))
        :else (clj->js x)))

(defn oset [o k v]
  (doto (if (nil? o) #js{} o)
    (aset k v)))

(defn oget [o k]
  (if (nil? o) nil (aget o k)))

(defn set-id-class
  "Takes the id and class from tag keyword, and adds them to the
  other props. Parsed tag is JS object with :id and :class properties."
  [props id-class]
  (let [id ($ id-class :id)
        class ($ id-class :class)]
    (cond-> props
      ;; Only use ID from tag keyword if no :id in props already
      (and (some? id)
           (nil? (:id props)))
      (assoc :id id)

      ;; Merge classes
      class
      (assoc :class (let [old-class (:class props)]
                      (if (nil? old-class) class (str class " " (if (named? old-class)
                                                                  (name old-class)
                                                                  old-class))))))))

(defn stringify-class [{:keys [class] :as props}]
  (if (coll? class)
    (->> class
         (keep (fn [c]
                 (if c
                   (if (named? c)
                     (name c)
                     c))))
         (string/join " ")
         (assoc props :class))
    props))

(defn convert-props [props id-class]
  (let [props (-> props
                  stringify-class
                  (set-id-class id-class))]
    (if ($ id-class :custom)
      (convert-custom-prop-value props)
      (convert-prop-value props))))

;;; Specialization for input components

;; This gets set from reagent.dom
(defonce find-dom-node nil)

;; <input type="??" >
;; The properites 'selectionStart' and 'selectionEnd' only exist on some inputs
;; See: https://html.spec.whatwg.org/multipage/forms.html#do-not-apply
(def these-inputs-have-selection-api #{"text" "textarea" "password" "search"
                                       "tel" "url"})

(defn ^boolean has-selection-api?
  [input-type]
  (contains? these-inputs-have-selection-api input-type))

(defn adapt-input-component [component]
  (fn [props & _]
    (comp/create-class
      {:display-name "InputWrapper"
       :get-initial-state
       (fn []
         #js {:value (:value props)})
       :should-component-update
       (fn [this old-argv new-args]
         true)
       :component-will-receive-props
       (fn [this [_ props]]
         (when (not= (:value props) (.. this -state -value))
           (.setState this #js {:value (:value props)})))
       :reagent-render
       (fn [props & children]
         (this-as this
           (let [props (if (or (not= "input" component)
                               (has-selection-api? (:type props)))
                         (-> props
                             (cond-> (:on-change props)
                               (assoc :on-change (fn [e]
                                                   (.setState this #js {:value (.. e -target -value)})
                                                   ((:on-change props) e))))
                             (cond-> (.. this -state -value)
                               (assoc :value (.. this -state -value)))
                             convert-prop-value)
                         (convert-prop-value props))]
             (apply react/createElement component props (map as-element children)))))})))

(declare make-element)

(def reagent-input
  (comp/reactify-component
    (adapt-input-component "input")))

(def reagent-textarea
  (comp/reactify-component
    (adapt-input-component "textarea")))

;;; Conversion from Hiccup forms

(defn parse-tag [hiccup-tag]
  (let [[tag id class] (->> hiccup-tag name (re-matches re-tag) next)
        class (when-not (nil? class)
                (string/replace class #"\." " "))]
    (assert tag (str "Invalid tag: '" hiccup-tag "'"
                     (comp/comp-name)))
    #js {:name tag
         :id id
         :class class
         ;; Custom element names must contain hyphen
         ;; https://www.w3.org/TR/custom-elements/#custom-elements-core-concepts
         :custom (not= -1 (.indexOf tag "-"))}))

(defn try-get-key [x]
  ;; try catch to avoid clojurescript peculiarity with
  ;; sorted-maps with keys that are numbers
  (try (get x :key)
       (catch :default e)))

(defn get-key [x]
  (when (map? x)
    (try-get-key x)))

(defn key-from-vec [v]
  (if-some [k (-> (meta v) get-key)]
    k
    (-> v (nth 1 nil) get-key)))

(defn reag-element [tag v]
  (let [c (comp/as-class tag)
        jsprops #js{:argv v}]
    (when-some [key (key-from-vec v)]
      ($! jsprops :key key))
    (react/createElement c jsprops)))

(defn fragment-element [argv]
  (let [props (nth argv 1 nil)
        hasprops (or (nil? props) (map? props))
        jsprops (convert-prop-value (if hasprops props))
        first-child (+ 1 (if hasprops 1 0))]
    (when-some [key (key-from-vec argv)]
      (oset jsprops "key" key))
    (make-element argv react/Fragment jsprops first-child)))

(defn adapt-react-class
  [c]
  (doto (->NativeWrapper)
    ($! :name c)
    ($! :id nil)
    ($! :class nil)))

(def tag-name-cache #js{})

(defn cached-parse [x]
  (if-some [s (cache-get tag-name-cache x)]
    s
    (aset tag-name-cache x (parse-tag x))))

(defn native-element [parsed argv first]
  (let [comp ($ parsed :name)
        props (nth argv first nil)
        hasprops (or (nil? props) (map? props))
        jsprops (convert-props (if hasprops props) parsed)
        first-child (+ first (if hasprops 1 0))
        key (-> (meta argv) get-key)
        jsprops (if (nil? key)
                  jsprops
                  (oset jsprops "key" key)) ]
    (case comp
      "input" (react/createElement reagent-input jsprops)
      "textarea" (react/createElement reagent-textarea jsprops)
      (make-element argv comp jsprops first-child))))

(defn str-coll [coll]
  (if (dev?)
    (str (prewalk (fn [x]
                    (if (fn? x)
                      (let [n (util/fun-name x)]
                        (case n "" x (symbol n)))
                      x)) coll))
    (str coll)))

(defn hiccup-err [v & msg]
  (str (apply str msg) ": " (str-coll v) "\n" (comp/comp-name)))

(defn vec-to-elem [v]
  (assert (pos? (count v)) (hiccup-err v "Hiccup form should not be empty"))
  (let [tag (nth v 0 nil)]
    (assert (valid-tag? tag) (hiccup-err v "Invalid Hiccup form"))
    (cond
      (keyword-identical? :<> tag)
      (fragment-element v)

      (hiccup-tag? tag)
      (let [n (name tag)
            pos (.indexOf n ">")]
        (case pos
          -1 (native-element (cached-parse n) v 1)
          ;; TODO: Doesn't this match :>foo or any keyword starting with >
          0 (let [comp (nth v 1 nil)]
              ;; Support [:> comp ...]
              (assert (= ">" n) (hiccup-err v "Invalid Hiccup tag"))
              (native-element #js{:name comp} v 2))
          ;; Support extended hiccup syntax, i.e :div.bar>a.foo
          ;; Apply metadata (e.g. :key) to the outermost element.
          ;; Metadata is probably used only with sequeneces, and in that case
          ;; only the key of the outermost element matters.
          (recur (with-meta [(subs n 0 pos)
                             (assoc (with-meta v nil) 0 (subs n (inc pos)))]
                            (meta v)))))

      (instance? NativeWrapper tag)
      (native-element tag v 1)

      :else (reag-element tag v))))

(declare expand-seq)
(declare expand-seq-check)

(defn as-element [x]
  (cond (js-val? x) x
        (vector? x) (vec-to-elem x)
        (seq? x) (if (dev?)
                   (expand-seq-check x)
                   (expand-seq x))
        (named? x) (name x)
        (satisfies? IPrintWithWriter x) (pr-str x)
        :else x))

(set! comp/as-element as-element)

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
          ($! o :no-key true))
        (aset a i (as-element val))))
    a))

(defn expand-seq-check [x]
  (let [ctx #js{}
        [res derefed] (ratom/check-derefs #(expand-seq-dev x ctx))]
    (when derefed
      (warn (hiccup-err x "Reactive deref not supported in lazy seq, "
                        "it should be wrapped in doall")))
    (when ($ ctx :no-key)
      (warn (hiccup-err x "Every element in a seq should have a unique :key")))
    res))

;; From https://github.com/babel/babel/commit/1d0e68f5a19d721fe8799b1ea331041d8bf9120e
;; (def react-element-type (or (and (exists? js/Symbol)
;;                                  ($ js/Symbol :for)
;;                                  ($ js/Symbol for "react.element"))
;;                             60103))

;; (defn make-element-fast [argv comp jsprops first-child]
;;   (let [key (some-> jsprops ($ :key))
;;         ref (some-> jsprops ($ :ref))
;;         props (if (nil? jsprops) (js-obj) jsprops)]
;;     ($! props :children
;;         (case (- (count argv) first-child)
;;           0 nil
;;           1 (as-element (nth argv first-child))
;;           (reduce-kv (fn [a k v]
;;                        (when (>= k first-child)
;;                          (.push a (as-element v)))
;;                        a)
;;                      #js[] argv)))
;;     (js-obj "key" key
;;             "ref" ref
;;             "props" props
;;             "$$typeof" react-element-type
;;             "type" comp
;;             ;; "_store" (js-obj)
;;             )))

(defn make-element [argv comp jsprops first-child]
  (case (- (count argv) first-child)
    ;; Optimize cases of zero or one child
    0 (react/createElement comp jsprops)

    1 (react/createElement comp jsprops
          (as-element (nth argv first-child nil)))

    (.apply react/createElement nil
            (reduce-kv (fn [a k v]
                         (when (>= k first-child)
                           (.push a (as-element v)))
                         a)
                       #js[comp jsprops] argv))))
