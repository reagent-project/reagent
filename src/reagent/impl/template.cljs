(ns reagent.impl.template
  (:require [react :as react]
            [clojure.string :as string]
            [reagent.impl.util :as util :refer [named?]]
            [reagent.impl.component :as comp]
            [reagent.impl.batching :as batch]
            [reagent.impl.input :as input]
            [reagent.impl.protocols :as p]
            [reagent.ratom :as ratom]
            [reagent.debug :refer-macros [dev? warn]]
            [goog.object :as gobj]))

;; From Weavejester's Hiccup, via pump:
(def ^{:doc "Regular expression that parses a CSS-style id and class
             from a tag name."}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(deftype NativeWrapper [tag id className])

(defn adapt-react-class
  [c]
  (->NativeWrapper c nil nil))

;;; Common utilities

(defn ^boolean hiccup-tag? [x]
  (or (named? x)
      (string? x)))

(defn ^boolean valid-tag? [x]
  (or (hiccup-tag? x)
      (ifn? x)
      (instance? NativeWrapper x)))

;;; Props conversion

;; TODO: Move prop-name caches to the compiler object, if this
;; conversion can be configured.

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

(defn make-element [this argv component jsprops first-child]
  (case (- (count argv) first-child)
    ;; Optimize cases of zero or one child
    0 (react/createElement component jsprops)

    1 (react/createElement component jsprops
                           (p/as-element this (nth argv first-child nil)))

    (.apply react/createElement nil
            (reduce-kv (fn [a k v]
                         (when (>= k first-child)
                          (.push a (p/as-element this v)))
                         a)
                       #js [component jsprops] argv))))

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

(defn reag-element [tag v compiler]
  (let [c (comp/as-class tag compiler)
        jsprops #js {}]
    (set! (.-argv jsprops) v)
    (when-some [key (util/react-key-from-vec v)]
      (set! (.-key jsprops) key))
    (react/createElement c jsprops)))

(defn function-element [tag v first-arg compiler]
  (let [jsprops #js {}]
    (set! (.-reagentRender jsprops) tag)
    (set! (.-argv jsprops) (subvec v first-arg))
    ; (set! (.-opts jsprops) opts)
    (when-some [key (util/react-key-from-vec v)]
      (set! (.-key jsprops) key))
    (react/createElement (comp/functional-render-fn compiler tag) jsprops)))

(defn maybe-function-element
  "If given tag is a Class, use it as a class,
  else wrap in Reagent function wrapper."
  [tag v compiler]
  (if (comp/react-class? tag)
    (reag-element tag v compiler)
    (function-element tag v 1 compiler)))

(defn fragment-element [argv compiler]
  (let [props (nth argv 1 nil)
        hasprops (or (nil? props) (map? props))
        jsprops (or (convert-prop-value (if hasprops props))
                    #js {})
        first-child (+ 1 (if hasprops 1 0))]
    (when-some [key (util/react-key-from-vec argv)]
      (set! (.-key jsprops) key))
    (p/make-element compiler argv react/Fragment jsprops first-child)))

(def tag-name-cache #js {})

(defn cached-parse [x]
  (if-some [s (cache-get tag-name-cache x)]
    s
    (let [v (parse-tag x)]
      (gobj/set tag-name-cache x v)
      v)))

(defn native-element [parsed argv first compiler]
  (let [component (.-tag parsed)
        props (nth argv first nil)
        hasprops (or (nil? props) (map? props))
        jsprops (or (convert-props (if hasprops props) parsed)
                    #js {})
        first-child (+ first (if hasprops 1 0))]
    (if (input/input-component? component)
      (let [input-class (or (.-reagentInput compiler)
                            (let [x (comp/create-class input/input-spec compiler)]
                              (set! (.-reagentInput compiler) x)
                              x))]
        (-> [input-class argv component jsprops first-child compiler]
            (with-meta (meta argv))
            (->> (p/as-element compiler))))
      (do
        (when-some [key (-> (meta argv) util/get-react-key)]
          (set! (.-key jsprops) key))
        (p/make-element compiler argv component jsprops first-child)))))

(defn raw-element [comp argv compiler]
  (let [props (nth argv 2 nil)
        jsprops (or props #js {})]
    (when-some [key (-> (meta argv) util/get-react-key)]
      (set! (.-key jsprops) key))
    (p/make-element compiler argv comp jsprops 3)))

(defn expand-seq [s compiler]
  (into-array (map #(p/as-element compiler %) s)))

(defn expand-seq-dev [s ^clj o compiler]
  (into-array (map (fn [val]
                     (when (and (vector? val)
                                (nil? (util/react-key-from-vec val)))
                       (set! (.-no-key o) true))
                     (p/as-element compiler val))
                   s)))

(defn expand-seq-check [x compiler]
  (let [ctx #js {}
        [res derefed] (ratom/check-derefs #(expand-seq-dev x ctx compiler))]
    (when derefed
      (warn (util/hiccup-err x (comp/comp-name) "Reactive deref not supported in lazy seq, "
                        "it should be wrapped in doall")))
    (when (.-no-key ctx)
      (warn (util/hiccup-err x (comp/comp-name) "Every element in a seq should have a unique :key")))
    res))

(defn hiccup-element [v compiler]
  (let [tag (nth v 0 nil)
        n (name tag)
        pos (.indexOf n ">")]
    (case pos
      -1 (native-element (cached-parse n) v 1 compiler)
      0 (assert (= ">" n) (util/hiccup-err v (comp/comp-name) "Invalid Hiccup tag"))
      ;; Support extended hiccup syntax, i.e :div.bar>a.foo
      ;; Apply metadata (e.g. :key) to the outermost element.
      ;; Metadata is probably used only with sequeneces, and in that case
      ;; only the key of the outermost element matters.
      (recur (with-meta [(subs n 0 pos)
                         (assoc (with-meta v nil) 0 (subs n (inc pos)))]
                        (meta v))
             compiler))))

(defn vec-to-elem [v compiler fn-to-element]
  (when (nil? compiler)
    (js/console.error "vec-to-elem" (pr-str v)))
  (assert (pos? (count v)) (util/hiccup-err v (comp/comp-name) "Hiccup form should not be empty"))
  (let [tag (nth v 0 nil)]
    (assert (valid-tag? tag) (util/hiccup-err v (comp/comp-name) "Invalid Hiccup form"))
    (case tag
      :> (native-element (->HiccupTag (nth v 1 nil) nil nil nil) v 2 compiler)
      :r> (raw-element (nth v 1 nil) v compiler)
      :f> (function-element (nth v 1 nil) v 2 compiler)
      :<> (fragment-element v compiler)
      (cond
       (hiccup-tag? tag)
       (hiccup-element v compiler)

       (instance? NativeWrapper tag)
       (native-element tag v 1 compiler)

       :else (fn-to-element tag v compiler)))))

(defn as-element [this x fn-to-element]
  (cond (util/js-val? x) x
        (vector? x) (vec-to-elem x this fn-to-element)
        (seq? x) (if (dev?)
                   (expand-seq-check x this)
                   (expand-seq x this))
        (named? x) (name x)
        (satisfies? IPrintWithWriter x) (pr-str x)
        :else x))

(defn create-compiler [opts]
  (let [id (gensym)
        fn-to-element (if (:function-components opts)
                        maybe-function-element
                        reag-element)]
    (reify p/Compiler
      ;; This is used to as cache key to cache component fns per compiler
      (get-id [this] id)
      (as-element [this x]
        (as-element this x fn-to-element))
      (make-element [this argv component jsprops first-child]
        (make-element this argv component jsprops first-child)))))

(def default-compiler* (create-compiler {}))
(def ^:dynamic default-compiler default-compiler*)

(defn set-default-compiler! [compiler]
  (set! default-compiler compiler))
