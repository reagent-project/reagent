(ns reagent.impl.template
  (:require [react :as react]
            [clojure.string :as string]
            [clojure.walk :refer [prewalk]]
            [reagent.impl.util :as util :refer [named?]]
            [reagent.impl.component :as comp]
            [reagent.impl.batching :as batch]
            [reagent.ratom :as ratom]
            [reagent.debug :refer-macros [dev? warn]]
            [goog.object :as gobj]))

(declare as-element)

;; From Weavejester's Hiccup, via pump:
(def ^{:doc "Regular expression that parses a CSS-style id and class
             from a tag name."}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(deftype NativeWrapper [tag id className])


;;; Common utilities

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
    (gobj/get o k)))

(defn cached-prop-name [k]
  (if (named? k)
    (if-some [k' (cache-get prop-name-cache (name k))]
      k'
      (let [v (util/dash-to-prop-name k)]
        (gobj/set prop-name-cache (name k) v)
        v))
    k))

(defn ^boolean js-val? [x]
  (not (identical? "object" (goog/typeOf x))))

(declare convert-prop-value)

(defn kv-conv [o k v]
  (doto o
    (gobj/set (cached-prop-name k) (convert-prop-value v))))

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
      (let [v (util/dash-to-prop-name k)]
        (gobj/set custom-prop-name-cache (name k) v)
        v))
    k))

(defn custom-kv-conv [o k v]
  (doto o
    (gobj/set (cached-custom-prop-name k) (convert-prop-value v))))

(defn convert-custom-prop-value [x]
  (cond (js-val? x) x
        (named? x) (name x)
        (map? x) (reduce-kv custom-kv-conv #js{} x)
        (coll? x) (clj->js x)
        (ifn? x) (fn [& args]
                   (apply x args))
        :else (clj->js x)))

(defn set-id-class
  "Takes the id and class from tag keyword, and adds them to the
  other props. Parsed tag is JS object with :id and :class properties."
  [props id-class]
  (let [id (.-id id-class)
        class (.-className id-class)]
    (cond-> props
      ;; Only use ID from tag keyword if no :id in props already
      (and (some? id)
           (nil? (:id props)))
      (assoc :id id)

      ;; Merge classes
      class
      (assoc :class (util/class-names class (:class props))))))

(defn convert-props [props ^clj id-class]
  (let [class (:class props)
        props (-> props
                  (cond-> class (assoc :class (util/class-names class)))
                  (set-id-class id-class))]
    (if (.-custom id-class)
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

(declare input-component-set-value)

(defn input-node-set-value
  [node rendered-value dom-value ^clj component {:keys [on-write]}]
  (if-not (and (identical? node (.-activeElement js/document))
            (has-selection-api? (.-type node))
            (string? rendered-value)
            (string? dom-value))
    ;; just set the value, no need to worry about a cursor
    (do
      (set! (.-cljsDOMValue component) rendered-value)
      (set! (.-value node) rendered-value)
      (when (fn? on-write)
        (on-write rendered-value)))

    ;; Setting "value" (below) moves the cursor position to the
    ;; end which gives the user a jarring experience.
    ;;
    ;; But repositioning the cursor within the text, turns out to
    ;; be quite a challenge because changes in the text can be
    ;; triggered by various events like:
    ;; - a validation function rejecting a user inputted char
    ;; - the user enters a lower case char, but is transformed to
    ;;   upper.
    ;; - the user selects multiple chars and deletes text
    ;; - the user pastes in multiple chars, and some of them are
    ;;   rejected by a validator.
    ;; - the user selects multiple chars and then types in a
    ;;   single new char to repalce them all.
    ;; Coming up with a sane cursor repositioning strategy hasn't
    ;; been easy ALTHOUGH in the end, it kinda fell out nicely,
    ;; and it appears to sanely handle all the cases we could
    ;; think of.
    ;; So this is just a warning. The code below is simple
    ;; enough, but if you are tempted to change it, be aware of
    ;; all the scenarios you have handle.
    (let [node-value (.-value node)]
      (if (not= node-value dom-value)
        ;; IE has not notified us of the change yet, so check again later
        (batch/do-after-render #(input-component-set-value component))
        (let [existing-offset-from-end (- (count node-value)
                                         (.-selectionStart node))
              new-cursor-offset        (- (count rendered-value)
                                         existing-offset-from-end)]
          (set! (.-cljsDOMValue component) rendered-value)
          (set! (.-value node) rendered-value)
          (when (fn? on-write)
            (on-write rendered-value))
          (set! (.-selectionStart node) new-cursor-offset)
          (set! (.-selectionEnd node) new-cursor-offset))))))

(defn input-component-set-value [^clj this]
  (when (.-cljsInputLive this)
    (set! (.-cljsInputDirty this) false)
    (let [rendered-value (.-cljsRenderedValue this)
          dom-value (.-cljsDOMValue this)
          ;; Default to the root node within this component
          node (find-dom-node this)]
      (when (not= rendered-value dom-value)
        (input-node-set-value node rendered-value dom-value this {})))))

(defn input-handle-change [^clj this on-change e]
  (set! (.-cljsDOMValue this) (-> e .-target .-value))
  ;; Make sure the input is re-rendered, in case on-change
  ;; wants to keep the value unchanged
  (when-not (.-cljsInputDirty this)
    (set! (.-cljsInputDirty this) true)
    (batch/do-after-render #(input-component-set-value this)))
  (on-change e))

(defn input-render-setup
  [^clj this ^js jsprops]
  ;; Don't rely on React for updating "controlled inputs", since it
  ;; doesn't play well with async rendering (misses keystrokes).
  (when (and (some? jsprops)
             (.hasOwnProperty jsprops "onChange")
             (.hasOwnProperty jsprops "value"))
    (assert find-dom-node
            "reagent.dom needs to be loaded for controlled input to work")
    (let [v (.-value jsprops)
          value (if (nil? v) "" v)
          on-change (.-onChange jsprops)]
      (when-not (.-cljsInputLive this)
        ;; set initial value
        (set! (.-cljsInputLive this) true)
        (set! (.-cljsDOMValue this) value))
      (set! (.-cljsRenderedValue this) value)
      (js-delete jsprops "value")
      (set! (.-defaultValue jsprops) value)
      (set! (.-onChange jsprops) #(input-handle-change this on-change %)))))

(defn input-unmount [^clj this]
  (set! (.-cljsInputLive this) nil))

(defn ^boolean input-component? [x]
  (case x
    ("input" "textarea") true
    false))

(def reagent-input-class nil)

(declare make-element)

(def input-spec
  {:display-name "ReagentInput"
   :component-did-update input-component-set-value
   :component-will-unmount input-unmount
   :reagent-render
   (fn [argv component jsprops first-child]
     (let [this comp/*current-component*]
       (input-render-setup this jsprops)
       (make-element argv component jsprops first-child)))})

(defn reagent-input
  []
  (when (nil? reagent-input-class)
    (set! reagent-input-class (comp/create-class input-spec)))
  reagent-input-class)


;;; Conversion from Hiccup forms

(deftype HiccupTag [tag id className custom])

(defn parse-tag [hiccup-tag]
  (let [[tag id className] (->> hiccup-tag name (re-matches re-tag) next)
        className (when-not (nil? className)
                    (string/replace className #"\." " "))]
    (assert tag (str "Invalid tag: '" hiccup-tag "'" (comp/comp-name)))
    (->HiccupTag tag
                 id
                 className
                 ;; Custom element names must contain hyphen
                 ;; https://www.w3.org/TR/custom-elements/#custom-elements-core-concepts
                 (not= -1 (.indexOf tag "-")))))

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
        jsprops #js {}]
    (set! (.-argv jsprops) v)
    (when-some [key (key-from-vec v)]
      (set! (.-key jsprops) key))
    (react/createElement c jsprops)))

(defn fragment-element [argv]
  (let [props (nth argv 1 nil)
        hasprops (or (nil? props) (map? props))
        jsprops (or (convert-prop-value (if hasprops props))
                    #js {})
        first-child (+ 1 (if hasprops 1 0))]
    (when-some [key (key-from-vec argv)]
      (set! (.-key jsprops) key))
    (make-element argv react/Fragment jsprops first-child)))

(defn adapt-react-class
  [c]
  (->NativeWrapper c nil nil))

(def tag-name-cache #js{})

(defn cached-parse [x]
  (if-some [s (cache-get tag-name-cache x)]
    s
    (let [v (parse-tag x)]
      (gobj/set tag-name-cache x v)
      v)))

(defn native-element [parsed argv first]
  (let [component (.-tag parsed)
        props (nth argv first nil)
        hasprops (or (nil? props) (map? props))
        jsprops (or (convert-props (if hasprops props) parsed)
                    #js {})
        first-child (+ first (if hasprops 1 0))]
    (if (input-component? component)
      (-> [(reagent-input) argv component jsprops first-child]
          (with-meta (meta argv))
          as-element)
      (do
        (when-some [key (-> (meta argv) get-key)]
          (set! (.-key jsprops) key))
        (make-element argv component jsprops first-child)))))

(defn str-coll [coll]
  (if (dev?)
    (str (prewalk (fn [x]
                    (if (fn? x)
                      (let [n (util/fun-name x)]
                        (case n
                          ("" nil) x
                          (symbol n)))
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
          0 (let [component (nth v 1 nil)]
              ;; Support [:> component ...]
              (assert (= ">" n) (hiccup-err v "Invalid Hiccup tag"))
              (native-element (->HiccupTag component nil nil nil) v 2))
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
  (into-array (map as-element s)))

(defn expand-seq-dev [s ^clj o]
  (into-array (map (fn [val]
                     (when (and (vector? val)
                                (nil? (key-from-vec val)))
                       (set! (.-no-key o) true))
                     (as-element val))
                   s)))

(defn expand-seq-check [x]
  (let [ctx #js{}
        [res derefed] (ratom/check-derefs #(expand-seq-dev x ctx))]
    (when derefed
      (warn (hiccup-err x "Reactive deref not supported in lazy seq, "
                        "it should be wrapped in doall")))
    (when (.-no-key ctx)
      (warn (hiccup-err x "Every element in a seq should have a unique :key")))
    res))

(defn make-element [argv component jsprops first-child]
  (case (- (count argv) first-child)
    ;; Optimize cases of zero or one child
    0 (react/createElement component jsprops)

    1 (react/createElement component jsprops
          (as-element (nth argv first-child nil)))

    (.apply react/createElement nil
            (reduce-kv (fn [a k v]
                         (when (>= k first-child)
                           (.push a (as-element v)))
                         a)
                       #js[component jsprops] argv))))
