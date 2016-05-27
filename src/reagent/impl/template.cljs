(ns reagent.impl.template
  (:require [clojure.string :as string]
            [clojure.walk :refer [prewalk]]
            [reagent.impl.util :as util :refer [is-client]]
            [reagent.impl.component :as comp]
            [reagent.impl.batching :as batch]
            [reagent.ratom :as ratom]
            [reagent.interop :refer-macros [$ $!]]
            [reagent.debug :refer-macros [dbg prn println log dev?
                                          warn warn-unless]]))

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

(defn oset [o k v]
  (doto (if (nil? o) #js{} o)
    (aset k v)))

(defn oget [o k]
  (if (nil? o) nil (aget o k)))

(defn set-id-class [p id-class]
  (let [id ($ id-class :id)
        p (if (and (some? id)
                   (nil? (oget p "id")))
            (oset p "id" id)
            p)]
    (if-some [class ($ id-class :className)]
      (let [old (oget p "className")]
        (oset p "className" (if (nil? old)
                              class
                              (str class " " old))))
      p)))

(defn convert-props [props id-class]
  (-> props
      convert-prop-value
      (set-id-class id-class)))


;;; Specialization for input components

;; This gets set from dom.cljs
(defonce find-dom-node nil)

(defn input-unmount [this]
  ($! this :cljsInputValue nil))

;; <input type="??" >
;; The properites 'selectionStart' and 'selectionEnd' only exist on some inputs
;; See: https://html.spec.whatwg.org/multipage/forms.html#do-not-apply
(def these-inputs-have-selection-api #{"text" "textarea" "password" "search"
                                       "tel" "url"})

(defn ^boolean has-selection-api?
  [input-type]
  (contains? these-inputs-have-selection-api input-type))

(defn input-set-value [this]
  (when-some [value ($ this :cljsInputValue)]
    ($! this :cljsInputDirty false)
    (let [node       (find-dom-node this)
          node-value ($ node :value)]
      (when (not= value node-value)
        (if-not (and (identical? node ($ js/document :activeElement))
                     (has-selection-api? ($ node :type))
                     (string? value)
                     (string? node-value))
          ;; just set the value, no need to worry about a cursor
          ($! node :value value)

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
          (let [existing-offset-from-end (- (count node-value)
                                            ($ node :selectionStart))
                new-cursor-offset        (- (count value)
                                            existing-offset-from-end)]
            ($! node :value value)
            ($! node :selectionStart new-cursor-offset)
            ($! node :selectionEnd   new-cursor-offset)))))))

(defn input-handle-change [this on-change e]
  (let [res (on-change e)]
    ;; Make sure the input is re-rendered, in case on-change
    ;; wants to keep the value unchanged
    (when-not ($ this :cljsInputDirty)
      ($! this :cljsInputDirty true)
      (batch/do-after-render #(input-set-value this)))
    res))

(defn input-render-setup [this jsprops]
  ;; Don't rely on React for updating "controlled inputs", since it
  ;; doesn't play well with async rendering (misses keystrokes).
  (if (and (some? find-dom-node)
           (some? jsprops)
           ($ jsprops hasOwnProperty "onChange")
           ($ jsprops hasOwnProperty "value"))
    (let [v ($ jsprops :value)
          value (if (nil? v) "" v)
          on-change ($ jsprops :onChange)]
      ($! this :cljsInputValue value)
      (js-delete jsprops "value")
      (doto jsprops
        ($! :defaultValue value)
        ($! :onChange #(input-handle-change this on-change %))))
    ($! this :cljsInputValue nil)))

(defn ^boolean input-component? [x]
  (case x
    ("input" "textarea") true
    false))

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

(defn reagent-input []
  (when (nil? reagent-input-class)
    (set! reagent-input-class (comp/create-class input-spec)))
  reagent-input-class)


;;; Conversion from Hiccup forms

(defn parse-tag [hiccup-tag]
  (let [[tag id class] (->> hiccup-tag name (re-matches re-tag) next)
        class (when-not (nil? class)
                (string/replace class #"\." " "))]
    (assert tag (str "Invalid tag: '" hiccup-tag "'"
                     (comp/comp-name)))
    #js{:name tag
        :id id
        :className class}))

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
    ($ util/react createElement c jsprops)))

(defn adapt-react-class [c]
  (doto (NativeWrapper.)
    ($! :name c)
    ($! :id nil)
    ($! :class nil)))

(def tag-name-cache #js{})

(defn cached-parse [x]
  (if-some [s (cache-get tag-name-cache x)]
    s
    (aset tag-name-cache x (parse-tag x))))

(declare as-element)

(defn native-element [parsed argv first]
  (let [comp ($ parsed :name)]
    (let [props (nth argv first nil)
          hasprops (or (nil? props) (map? props))
          jsprops (convert-props (if hasprops props) parsed)
          first-child (+ first (if hasprops 1 0))]
      (if (input-component? comp)
        (-> [(reagent-input) argv comp jsprops first-child]
            (with-meta (meta argv))
            as-element)
        (let [key (-> (meta argv) get-key)
              p (if (nil? key)
                  jsprops
                  (oset jsprops "key" key))]
          (make-element argv comp p first-child))))))

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
      (hiccup-tag? tag)
      (let [n (name tag)
            pos (.indexOf n ">")]
        (case pos
          -1 (native-element (cached-parse n) v 1)
          0 (let [comp (nth v 1 nil)]
              ;; Support [:> comp ...]
              (assert (= ">" n) (hiccup-err v "Invalid Hiccup tag"))
              (assert (or (string? comp) (fn? comp))
                      (hiccup-err v "Expected React component in"))
              (native-element #js{:name comp} v 2))
          ;; Support extended hiccup syntax, i.e :div.bar>a.foo
          (recur [(subs n 0 pos)
                  (assoc v 0 (subs n (inc pos)))])))

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
    0 ($ util/react createElement comp jsprops)

    1 ($ util/react createElement comp jsprops
          (as-element (nth argv first-child nil)))

    (.apply ($ util/react :createElement) nil
            (reduce-kv (fn [a k v]
                         (when (>= k first-child)
                           (.push a (as-element v)))
                         a)
                       #js[comp jsprops] argv))))
