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

(def ^:dynamic *force-set-dom-value* false)

(defn get-true-active-element []
  ;; For controlled inputs `input-node-set-value` tries to do some heurestics to
  ;; preserve the cursor position. But before doing so, it tries to find the active
  ;; element in the document.
  ;;
  ;; Unfortunately, if an input element is inside a shadow dom, the
  ;; activeElement points to the shadow root instead of the input element. This
  ;; messes up the cursor perservation logic.
  ;;
  ;; But the saving grace here is that active input element can still be found
  ;; by querying for the `.activeElement`` on the shadow-root.
  (loop [active-element (.-activeElement js/document)]
    (if-let [shadow-root (.-shadowRoot active-element)]
      (recur (.-activeElement shadow-root))
      active-element)))

(defn- set-cursor-from-end!
  "Put the cursor offset characters from the end of value, the node's new
  value. A negative position ends up at the end of the text: the setters
  take an unsigned long and clamp it to the length."
  [node value offset]
  (let [pos (- (count value) offset)]
    (set! (.-selectionStart node) pos)
    (set! (.-selectionEnd node) pos)))

(defn input-node-set-value
  [node rendered-value dom-value ^clj component {:keys [on-write]}]
  (if (or (not (and (identical? node (get-true-active-element))
                    (has-selection-api? (.-type node))
                    (string? rendered-value)
                    (string? dom-value)))
          *force-set-dom-value*)
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
        ;; IE has not notified us of the change yet, so check again later.
        ;; Issue #566: Also setup force flag, so that cljsDOMValue will be set
        ;; to dom elements current value, even if the input is activeElement.
        ;; This fixes cases where input is focused from the code, before React
        ;; render is called and input-component-set-value and
        ;; input-node-set-value would be called infinitely.
        (batch/do-after-render #(binding [*force-set-dom-value* true]
                                  (input-component-set-value component)))
        (let [existing-offset-from-end (- (count node-value)
                                          (.-selectionStart node))]
          (set! (.-cljsDOMValue component) rendered-value)
          (set! (.-value node) rendered-value)
          (when (fn? on-write)
            (on-write rendered-value))
          (set-cursor-from-end! node rendered-value existing-offset-from-end))))))

(defn input-component-set-value [^clj this]
  (when (.-cljsInputLive this)
    (let [rendered-value (.-cljsRenderedValue this)
          dom-value (.-cljsDOMValue this)
          ;; Default to the root node within this component
          node (.-inputEl this)]
      (if (= rendered-value dom-value)
        ;; State has caught up with the DOM.
        (set! (.-cljsPendingValues this) nil)
        (let [pending (.-cljsPendingValues this)
              i (if (some? pending) (.indexOf pending rendered-value) -1)]
          (if (>= i 0)
            ;; The rendered value is one that on-change already reported, but
            ;; the user has typed more since: the state lags behind the DOM,
            ;; e.g. because on-change updates the state on a later tick (like
            ;; re-frame dispatch). Writing it would lose the newer input, so
            ;; only forget the values up to it.
            (.splice pending 0 (inc i))
            (do (set! (.-cljsPendingValues this) nil)
                (input-node-set-value node rendered-value dom-value this {}))))))))

(defn input-component-did-update [^clj this]
  (let [rendered-value (.-cljsRenderedValue this)
        committed-value (.-cljsCommittedValue this)]
    (set! (.-cljsCommittedValue this) rendered-value)
    ;; A render that didn't change the value (e.g. a parent re-rendered) while
    ;; on-change values are pending carries state that lags behind the DOM:
    ;; its value was rendered before the user typed the pending values.
    ;; Writing it would lose them.
    (when-not (and (some? (.-cljsPendingValues this))
                   (= rendered-value committed-value))
      (input-component-set-value this))))

(defn input-handle-change [^clj this on-change e]
  ;; The DOM is the truth while the user types: the typed value is only
  ;; replaced when the input renders a new value that on-change didn't report.
  ;; If on-change keeps the state unchanged (rejects the change), the typed
  ;; text stays: Reagent can't tell that apart from state that is updated
  ;; later (e.g. re-frame dispatch while the event queue is busy), and
  ;; restoring the rendered value then would lose keys (#676). To reject a
  ;; change, on-change can set the node's value itself.
  (let [node (.-target e)
        v (.-value node)
        cursor (when (has-selection-api? (.-type node))
                 (.-selectionStart node))]
    (set! (.-cljsDOMValue this) v)
    (when (nil? (.-cljsPendingValues this))
      (set! (.-cljsPendingValues this) #js []))
    (.push (.-cljsPendingValues this) v)
    (on-change e)
    (let [nv (.-value node)]
      (when-not (identical? nv v)
        ;; on-change wrote the node: that's the text in the DOM now, and the
        ;; typed value won't be rendered.
        (set! (.-cljsDOMValue this) nv)
        (when-some [pending (.-cljsPendingValues this)]
          (.pop pending)
          ;; nil means nothing is pending, see input-component-did-update.
          (when (zero? (.-length pending))
            (set! (.-cljsPendingValues this) nil)))
        ;; Writing the value moved the cursor to the end. Keep its distance
        ;; from the end instead, like input-node-set-value, unless on-change
        ;; moved the cursor itself.
        (when (and (some? cursor)
                   (== (count nv) (.-selectionStart node) (.-selectionEnd node)))
          (set-cursor-from-end! node nv (- (count v) cursor)))))))

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
        (set! (.-cljsDOMValue this) value)
        (set! (.-cljsCommittedValue this) value))
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

(defn input-mount [^clj this]
  ;; React 18+ StrictMode unmounts and remounts the component right after
  ;; mounting it, without rendering in between (#688). Mark the input live
  ;; again, without resetting the DOM value like the first render does: the
  ;; user may have typed already. The ref fn is only set up for controlled
  ;; inputs.
  (when (some? (.-reagentRefFn this))
    (set! (.-cljsInputLive this) true)))

(defn input-unmount [^clj this]
  (set! (.-cljsInputLive this) nil))

(defn ^boolean input-component? [x]
  (case x
    ("input" "textarea") true
    false))

(def input-spec
  {:display-name "ReagentInput"
   :component-did-mount input-mount
   :component-did-update input-component-did-update
   :component-will-unmount input-unmount
   :reagent-render
   (fn [argv component jsprops first-child compiler]
     (let [this comp/*current-component*]
       (input-render-setup this jsprops)
       (p/make-element compiler argv component jsprops first-child)))})
