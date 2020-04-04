(ns reagent.impl.template
  (:require [react :as react]
            [clojure.string :as string]
            [clojure.walk :refer [prewalk]]
            [reagent.impl.util :as util :refer [named?]]
            [reagent.impl.component :as comp]
            [reagent.impl.batching :as batch]
            [reagent.impl.input :as input]
            [reagent.ratom :as ratom]
            [reagent.debug :refer-macros [dev? warn]]
            [goog.object :as gobj]))

(declare as-element make-element)

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

(declare convert-prop-value)

(defn kv-conv [o k v]
  (doto o
    (gobj/set (cached-prop-name k) (convert-prop-value v))))

(defn convert-prop-value [x]
  (cond (util/js-val? x) x
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
  (cond (util/js-val? x) x
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
      ;; Note: someone might use React-style :className property,
      ;; this is the only place where that needs special case. Using
      ;; :class and :className together is not supported.
      (assoc :class (util/class-names class (or (:class props) (:className props)))))))

(defn convert-props [props ^clj id-class]
  (let [class (:class props)
        props (-> props
                  (cond-> class (assoc :class (util/class-names class)))
                  (set-id-class id-class))]
    (if (.-custom id-class)
      (convert-custom-prop-value props)
      (convert-prop-value props))))

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

(defn reag-element [tag v opts]
  (let [c (comp/as-class tag opts)
        jsprops #js {}]
    (set! (.-argv jsprops) v)
    (when-some [key (util/react-key-from-vec v)]
      (set! (.-key jsprops) key))
    (react/createElement c jsprops)))

(defn functional-reag-element [tag v opts]
  (if (or (comp/react-class? tag)
          ;; TODO: Should check others for real comptibility, this fixes tests
          ;; TODO: Drop support for fn + meta for Class component methods?
          (:should-component-update (meta tag)))
    ;; as-class unncessary later as tag is always class
    (let [c (comp/as-class tag opts)
          jsprops #js {}]
      (set! (.-argv jsprops) v)
      (when-some [key (util/react-key-from-vec v)]
        (set! (.-key jsprops) key))
      (react/createElement c jsprops))
    (let [jsprops #js {}]
      (set! (.-reagentRender jsprops) tag)
      (set! (.-argv jsprops) (subvec v 1))
      (set! (.-opts jsprops) opts)
      (when-some [key (util/react-key-from-vec v)]
        (set! (.-key jsprops) key))
      (react/createElement (comp/funtional-render-fn tag) jsprops))))

(defn fragment-element [argv opts]
  (let [props (nth argv 1 nil)
        hasprops (or (nil? props) (map? props))
        jsprops (or (convert-prop-value (if hasprops props))
                    #js {})
        first-child (+ 1 (if hasprops 1 0))]
    (when-some [key (util/react-key-from-vec argv)]
      (set! (.-key jsprops) key))
    (make-element argv react/Fragment jsprops first-child opts)))

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

(defn native-element [parsed argv first opts]
  (let [component (.-tag parsed)
        props (nth argv first nil)
        hasprops (or (nil? props) (map? props))
        jsprops (or (convert-props (if hasprops props) parsed)
                    #js {})
        first-child (+ first (if hasprops 1 0))]
    (if (input/input-component? component)
      (-> [(input/reagent-input) argv component jsprops first-child opts]
          (with-meta (meta argv))
          (as-element opts))
      (do
        (when-some [key (-> (meta argv) util/get-react-key)]
          (set! (.-key jsprops) key))
        (make-element argv component jsprops first-child opts)))))

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

(defn vec-to-elem [v opts]
  (assert (pos? (count v)) (hiccup-err v "Hiccup form should not be empty"))
  (let [tag (nth v 0 nil)]
    (assert (valid-tag? tag) (hiccup-err v "Invalid Hiccup form"))
    (cond
      (keyword-identical? :<> tag)
      (fragment-element v opts)

      (hiccup-tag? tag)
      (let [n (name tag)
            pos (.indexOf n ">")]
        (case pos
          -1 (native-element (cached-parse n) v 1 opts)
          0 (let [component (nth v 1 nil)]
              ;; Support [:> component ...]
              (assert (= ">" n) (hiccup-err v "Invalid Hiccup tag"))
              (native-element (->HiccupTag component nil nil nil) v 2 opts))
          ;; Support extended hiccup syntax, i.e :div.bar>a.foo
          ;; Apply metadata (e.g. :key) to the outermost element.
          ;; Metadata is probably used only with sequeneces, and in that case
          ;; only the key of the outermost element matters.
          (recur (with-meta [(subs n 0 pos)
                             (assoc (with-meta v nil) 0 (subs n (inc pos)))]
                            (meta v))
                 opts)))

      (instance? NativeWrapper tag)
      (native-element tag v 1 opts)

      :else (if (:functional-reag-elements? opts)
              (functional-reag-element tag v opts)
              (reag-element tag v opts)))))

(declare expand-seq)
(declare expand-seq-check)

(defn as-element [x opts]
  (cond (util/js-val? x) x
        (vector? x) (vec-to-elem x opts)
        (seq? x) (if (dev?)
                   (expand-seq-check x opts)
                   (expand-seq x opts))
        (named? x) (name x)
        (satisfies? IPrintWithWriter x) (pr-str x)
        :else x))

(set! comp/as-element as-element)

(defn expand-seq [s opts]
  (into-array (map as-element s)))

(defn expand-seq-dev [s ^clj o opts]
  (into-array (map (fn [val]
                     (when (and (vector? val)
                                (nil? (util/react-key-from-vec val)))
                       (set! (.-no-key o) true))
                     (as-element val opts))
                   s)))

(defn expand-seq-check [x opts]
  (let [ctx #js{}
        [res derefed] (ratom/check-derefs #(expand-seq-dev x ctx opts))]
    (when derefed
      (warn (hiccup-err x "Reactive deref not supported in lazy seq, "
                        "it should be wrapped in doall")))
    (when (.-no-key ctx)
      (warn (hiccup-err x "Every element in a seq should have a unique :key")))
    res))

(defn make-element [argv component jsprops first-child opts]
  (case (- (count argv) first-child)
    ;; Optimize cases of zero or one child
    0 (react/createElement component jsprops)

    1 (react/createElement component jsprops
          (as-element (nth argv first-child nil) opts))

    (.apply react/createElement nil
            (reduce-kv (fn [a k v]
                         (when (>= k first-child)
                           (.push a (as-element v opts)))
                         a)
                       #js[component jsprops] argv))))

(set! input/make-element make-element)
