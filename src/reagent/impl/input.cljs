(ns reagent.impl.input
  (:require [reagent.impl.component :as comp]
            [reagent.impl.batching :as batch]
            [reagent.impl.protocols :as p]))

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
          node (.-inputEl this)]
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
    (let [v (.-value jsprops)
          value (if (nil? v) "" v)
          on-change (.-onChange jsprops)
          original-ref-fn (.-ref jsprops)]
      (when-not (.-cljsInputLive this)
        ;; set initial value
        (set! (.-cljsInputLive this) true)
        (set! (.-cljsDOMValue this) value))
      (when-not (.-reagentRefFn this)
        (set! (.-reagentRefFn this)
              (cond
                ;; ref fn
                (fn? original-ref-fn)
                (fn [el]
                  (set! (.-inputEl this) el)
                  (original-ref-fn el))

                ;; react/createRef object
                (and original-ref-fn (.hasOwnProperty original-ref-fn "current"))
                (fn [el]
                  (set! (.-inputEl this) el)
                  (set! (.-current original-ref-fn) el))

                :else
                (fn [el]
                  (set! (.-inputEl this) el)))))
      (set! (.-cljsRenderedValue this) value)
      (js-delete jsprops "value")
      (set! (.-defaultValue jsprops) value)
      (set! (.-onChange jsprops) #(input-handle-change this on-change %))
      (set! (.-ref jsprops) (.-reagentRefFn this)))))

(defn input-unmount [^clj this]
  (set! (.-cljsInputLive this) nil))

(defn ^boolean input-component? [x]
  (case x
    ("input" "textarea") true
    false))

(def input-spec
  {:display-name "ReagentInput"
   :component-did-update input-component-set-value
   :component-will-unmount input-unmount
   :reagent-render
   (fn [argv component jsprops first-child compiler]
     (let [this comp/*current-component*]
       (input-render-setup this jsprops)
       (p/make-element compiler argv component jsprops first-child)))})
