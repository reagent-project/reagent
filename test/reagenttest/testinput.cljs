(ns reagenttest.testinput
  "Tests for the controlled input workaround in reagent.impl.input.

  Typing is simulated by setting the DOM value with the native setter
  (bypassing React's value tracker, like a real keystroke does) and dispatching
  an `input` event, which React turns into `onChange`.

  The render queue is flushed manually with `r/flush`, so tests decide exactly
  when Reagent renders relative to keystrokes and state updates. A real browser
  can run the render queue (requestAnimationFrame) at any of these points.

  Real, trusted input (caret movement in email/number inputs, IME
  composition, scrolling of a full input, typing speed) is tested with
  Playwright in `e2e/inputs.spec.js`. These tests cover what that can't pin
  down: exact orderings of keystrokes, renders and state updates, and that
  Reagent doesn't write node.value at all when the state follows the typed
  text. They also run in CI, the e2e specs don't."
  (:require [clojure.test :refer-macros [is testing]]
            [clojure.string :as string]
            [promesa.core :as p]
            [reagent.core :as r]
            [reagenttest.utils :as u]))

;;; Helpers

(defn- native-value-setter [node]
  (.-set (js/Object.getOwnPropertyDescriptor (js/Object.getPrototypeOf node) "value")))

(defn- set-native-value! [node v]
  (.call (native-value-setter node) node v))

(defn- type-text!
  "Simulate user typing text s at the current selection, replacing the
  selected text."
  [node s]
  (let [v (.-value node)
        start (.-selectionStart node)
        end (.-selectionEnd node)
        pos (+ start (count s))]
    (set-native-value! node (str (subs v 0 start) s (subs v end)))
    (.setSelectionRange node pos pos)
    (.dispatchEvent node (js/InputEvent. "input" #js {:bubbles true}))))

(defn- type-keys!
  "Type each character of s separately, calling (between) after each key."
  [node s between]
  (doseq [c s]
    (type-text! node c)
    (between)))

(defn- set-cursor! [node pos]
  (.setSelectionRange node pos pos))

(defn- spy-writes!
  "Record every value Reagent (or anyone else) writes through node.value.
  Simulated typing uses the prototype setter and isn't recorded. Returns an
  atom with the written values."
  [node]
  (let [writes (atom [])
        ;; React defines its own value property on the instance to track the
        ;; value, wrap that one.
        desc (or (js/Object.getOwnPropertyDescriptor node "value")
                 (js/Object.getOwnPropertyDescriptor (js/Object.getPrototypeOf node) "value"))]
    (js/Object.defineProperty
     node "value"
     #js {:configurable true
          :get (fn [] (this-as this (.call (.-get desc) this)))
          :set (fn [v]
                 (this-as this
                   (swap! writes conj v)
                   (.call (.-set desc) this v)))})
    writes))

(defn- change-check!
  "Let the check that restores the rendered value after a change run: it is
  scheduled with a setTimeout after on-change and then waits for a render."
  []
  (p/do (p/delay 0)
        (r/flush)))

(defn- with-focused-input
  "Render comp into a container attached to the document, focus the first
  input or textarea and call (f node), which can return a promise. The
  container needs to be in the document for focus and so for the cursor
  handling of the workaround. options are passed to `u/with-render*`."
  ([comp f] (with-focused-input comp u/*test-compiler* f))
  ([comp options f]
   (u/with-render* comp options
     (fn [div]
       (.appendChild (.-body js/document) div)
       (-> (p/do
             (let [node (.querySelector div "input, textarea")]
               (.focus node)
               (f node)))
           (p/finally (fn [] (.remove div))))))))

(defn- async-state
  "State container that applies updates later, like re-frame's dispatch,
  which runs the event handler on a later tick. Returns [state dispatch!
  process!], process! applies the queued updates in order. Updates for which
  (accept? v) is false are dropped, like an event handler that keeps the db
  unchanged."
  ([init] (async-state init (constantly true)))
  ([init accept?]
   (let [state (r/atom init)
         queue (atom [])]
     [state
      (fn dispatch! [v] (swap! queue conj v))
      (fn process! []
        (let [q @queue]
          (reset! queue [])
          (doseq [v q]
            (when (accept? v)
              (reset! state v)))))])))

;;; Synchronous on-change, the case the workaround was written for

(u/deftest ^:dom sync-on-change-typing
  (let [state (r/atom "")
        c (fn []
            [:input {:type "text"
                     :value @state
                     :on-change #(reset! state (.. % -target -value))}])]
    (u/async
      (with-focused-input [c]
        (fn [node]
          (let [writes (spy-writes! node)]
            (testing "render after every key"
              (type-keys! node "abc" r/flush)
              (is (= "abc" @state (.-value node))))

            (testing "several keys between renders"
              (type-keys! node "def" (fn []))
              (r/flush)
              (is (= "abcdef" @state (.-value node))))

            (testing "typing in the middle keeps the cursor"
              (set-cursor! node 3)
              (type-keys! node "XY" r/flush)
              (is (= "abcXYdef" @state (.-value node)))
              (is (= 5 (.-selectionStart node) (.-selectionEnd node))))

            (p/do
              (change-check!)
              (testing "state equal to the typed text is never written to the node"
                (is (= [] @writes))))))))))

;;; on-change that changes the typed text: the workaround writes the rendered
;;; value and restores the cursor.

(u/deftest ^:dom on-change-rejects-key
  ;; Documented behavior: if on-change doesn't update the state, the input
  ;; goes back to the rendered value.
  (let [state (r/atom "12")
        c (fn []
            [:input {:type "text"
                     :value @state
                     :on-change (fn [e]
                                  (let [v (.. e -target -value)]
                                    (when (re-matches #"\d*" v)
                                      (reset! state v))))}])]
    (u/async
      (with-focused-input [c]
        (fn [node]
          (set-cursor! node 1)
          (type-text! node "x")
          (is (= "1x2" (.-value node)))
          (r/flush)
          (is (= "1x2" (.-value node)) "not restored before the change check")
          (p/do
            (change-check!)
            (is (= "12" @state (.-value node)))
            (is (= 1 (.-selectionStart node)) "cursor stays where the key was typed")

            (type-text! node "3")
            (change-check!)
            (is (= "132" @state (.-value node)))
            (is (= 2 (.-selectionStart node)))))))))

(u/deftest ^:dom on-change-transforms-value-unicode
  ;; Cursor position is counted in UTF-16 code units by both the Selection
  ;; API and `count`, characters outside the BMP (emoji) take two units.
  (let [state (r/atom "😀ab😀cd")
        c (fn []
            [:input {:type "text"
                     :value @state
                     :on-change #(reset! state (string/upper-case (.. % -target -value)))}])]
    (u/async
      (with-focused-input [c]
        (fn [node]
          ;; after "😀ab"
          (set-cursor! node 4)
          (type-text! node "x")
          (r/flush)
          (is (= "😀ABX😀CD" @state (.-value node)))
          (is (= 5 (.-selectionStart node)))

          (type-text! node "😀")
          (r/flush)
          (is (= "😀ABX😀😀CD" @state (.-value node)))
          (is (= 7 (.-selectionStart node))))))))

(u/deftest ^:dom state-changed-from-outside
  (let [state (r/atom "foo")
        c (fn []
            [:input {:type "text"
                     :value @state
                     :on-change #(reset! state (.. % -target -value))}])]
    (u/async
      (with-focused-input [c]
        (fn [node]
          (type-text! node "x")
          (r/flush)
          (is (= "foox" (.-value node)))
          ;; e.g. a reset button
          (reset! state "")
          (r/flush)
          (is (= "" (.-value node)))
          (type-text! node "a")
          (r/flush)
          (is (= "a" @state (.-value node))))))))

;;; Asynchronous on-change (#676, re-frame dispatch)
;;
;; The state is updated on a later tick than the change event. Reagent can
;; render between the keystroke and the state update, with a state that is
;; older than the text in the node. The real-timing version of these is
;; "fast typing (#676)" in e2e/inputs.spec.js.

(u/deftest ^:dom async-on-change-state-catches-up-before-render
  ;; Baseline: state is updated before every render.
  (let [[state dispatch! process!] (async-state "")
        c (fn []
            [:input {:type "text"
                     :value @state
                     :on-change #(dispatch! (.. % -target -value))}])]
    (u/async
      (with-focused-input [c]
        (fn [node]
          (let [writes (spy-writes! node)]
            (type-keys! node "abc" (fn [] (process!) (r/flush)))
            (is (= "abc" @state (.-value node)))
            (is (= [] @writes))))))))

(u/deftest ^:dom async-on-change-render-before-state-update
  ;; Render (animation frame) runs between the keystroke and the state
  ;; update. Reagent mustn't put the old state into the node, as the user can
  ;; type the next key before the state catches up: "a", render, "b" would
  ;; end up as "b". The check for a rejected change (see on-change-rejects-key)
  ;; waits for a setTimeout after on-change, so the state has a chance to
  ;; update first.
  (let [[state dispatch! process!] (async-state "")
        c (fn []
            [:input {:type "text"
                     :value @state
                     :on-change #(dispatch! (.. % -target -value))}])]
    (u/async
      (with-focused-input [c]
        (fn [node]
          (let [writes (spy-writes! node)]
            (type-text! node "a")
            (r/flush)
            (is (= "a" (.-value node)) "typed key stays in the node")
            (type-text! node "b")
            (process!)
            (r/flush)
            (is (= "ab" @state (.-value node)))
            (is (= [] @writes))))))))

(u/deftest ^:dom async-on-change-render-with-lagging-state
  ;; The scenario from #676: state update for key n-1 triggers a render,
  ;; while key n is already in the node but not yet in the state. The render
  ;; mustn't write the text after key n-1 into the node, or key n+1 is
  ;; appended to that and key n is lost.
  (let [[state dispatch! process!] (async-state "")
        c (fn []
            [:input {:type "text"
                     :value @state
                     :on-change #(dispatch! (.. % -target -value))}])]
    (u/async
      (with-focused-input [c]
        (fn [node]
          (let [writes (spy-writes! node)]
            (type-text! node "a")
            (process!)
            ;; state = "a", node = "ab"
            (type-text! node "b")
            (r/flush)
            (type-text! node "c")
            (process!)
            (r/flush)
            (is (= "abc" @state (.-value node)) "no lost keys")
            (is (= [] @writes))))))))

(u/deftest ^:dom async-on-change-rerender-with-lagging-state
  ;; #676 (comment): something else re-renders the input (e.g. its parent,
  ;; when a background request finishes) after a key that isn't in the state
  ;; yet. The render has the value that was already rendered before the key,
  ;; which on-change no longer lists as pending, and mustn't be written.
  (let [[state dispatch! process!] (async-state "")
        other (r/atom 0)
        c (fn []
            @other
            [:input {:type "text"
                     :value @state
                     :on-change #(dispatch! (.. % -target -value))}])]
    (u/async
      (with-focused-input [c]
        (fn [node]
          (let [writes (spy-writes! node)]
            (type-text! node "a")
            (process!)
            (r/flush)
            ;; state = "a", node = "ab"
            (type-text! node "b")
            (swap! other inc)
            (r/flush)
            (is (= "ab" (.-value node)) "typed key stays in the node")
            (type-text! node "c")
            (process!)
            (r/flush)
            (is (= "abc" @state (.-value node)) "no lost keys")
            (is (= [] @writes))))))))

(u/deftest ^:dom async-on-change-external-change-while-pending
  ;; Only a render that keeps the value unchanged is ignored while on-change
  ;; values are pending (see async-on-change-rerender-with-lagging-state). A
  ;; new value from elsewhere (e.g. a reset button) is written right away.
  (let [[state dispatch!] (async-state "ab")
        c (fn []
            [:input {:type "text"
                     :value @state
                     :on-change #(dispatch! (.. % -target -value))}])]
    (u/async
      (with-focused-input [c]
        (fn [node]
          (let [writes (spy-writes! node)]
            (type-text! node "c")
            (reset! state "")
            (r/flush)
            (is (= "" (.-value node)))
            (is (= [""] @writes))))))))

(u/deftest ^:dom async-on-change-fast-typing
  ;; Same as above with more keys and a render after every key, state lags
  ;; one key behind.
  (let [[state dispatch! process!] (async-state "")
        c (fn []
            [:input {:type "text"
                     :value @state
                     :on-change #(dispatch! (.. % -target -value))}])
        text "abcdefghij"]
    (u/async
      (with-focused-input [c]
        (fn [node]
          (doseq [ch text]
            ;; previous key reaches the state, then new key is typed before
            ;; the render
            (process!)
            (type-text! node ch)
            (r/flush))
          (process!)
          (r/flush)
          (is (= text @state (.-value node))))))))

(u/deftest ^:dom async-on-change-rejects-key
  ;; Async on-change can still reject a change: once the state had a chance
  ;; to update and didn't change, the rendered value is restored.
  (let [[state dispatch! process!] (async-state "ab" #(not (re-find #"\d" %)))
        c (fn []
            [:input {:type "text"
                     :value @state
                     :on-change #(dispatch! (.. % -target -value))}])]
    (u/async
      (with-focused-input [c]
        (fn [node]
          (type-text! node "1")
          (r/flush)
          (is (= "ab1" (.-value node)))
          (process!)
          (p/do
            (change-check!)
            (is (= "ab" @state (.-value node)))
            (type-text! node "c")
            ;; state update arrives before the change check
            (p/delay 0)
            (process!)
            (r/flush)
            (is (= "abc" @state (.-value node)))))))))

(u/deftest ^:dom async-on-change-rejects-key-with-rerender
  ;; A re-render with the unchanged value is ignored while on-change values
  ;; are pending (see async-on-change-rerender-with-lagging-state), but the
  ;; change check still restores the value of a rejected change.
  (let [[state dispatch! process!] (async-state "ab" #(not (re-find #"\d" %)))
        other (r/atom 0)
        c (fn []
            @other
            [:input {:type "text"
                     :value @state
                     :on-change #(dispatch! (.. % -target -value))}])]
    (u/async
      (with-focused-input [c]
        (fn [node]
          (type-text! node "1")
          (process!)
          (swap! other inc)
          (r/flush)
          (is (= "ab1" (.-value node)))
          (p/do
            (change-check!)
            (is (= "ab" @state (.-value node)))))))))

(u/deftest ^:dom async-on-change-transform
  ;; Async state can still transform the value, once the state has caught up
  ;; with the typed text the transformed value should be shown.
  (let [[state dispatch! process!] (async-state "")
        c (fn []
            [:input {:type "text"
                     :value @state
                     :on-change #(dispatch! (string/upper-case (.. % -target -value)))}])]
    (u/async
      (with-focused-input [c]
        (fn [node]
          (type-text! node "a")
          (r/flush)
          (type-text! node "b")
          (process!)
          (r/flush)
          (is (= "AB" @state (.-value node))))))))

;;; StrictMode (#688)
;;
;; In development builds React 18+ StrictMode unmounts and remounts the
;; component right after mounting (componentDidMount, componentWillUnmount,
;; componentDidMount). The workaround must still work after that.

(defn- strict
  "Test options for rendering in StrictMode. StrictMode has to be at the root:
  React doesn't remount components inside a StrictMode element that is
  mounted inside a non-strict tree."
  []
  (assoc u/*test-compiler* :strict? true :strict-mode? true))

(u/deftest ^:dom strict-mode-rejects-first-key
  (let [state (r/atom "12")
        c (fn []
            [:input {:type "text"
                     :value @state
                     :on-change (fn [e]
                                  (let [v (.. e -target -value)]
                                    (when (re-matches #"\d*" v)
                                      (reset! state v))))}])]
    (u/async
      (with-focused-input [c] (strict)
        (fn [node]
          (set-cursor! node 1)
          (type-text! node "x")
          (is (= "1x2" (.-value node)))
          (p/do
            (change-check!)
            (is (= "12" @state (.-value node)) "first key after mount is reverted")
            (is (= 1 (.-selectionStart node)))))))))

(u/deftest ^:dom strict-mode-async-on-change-lagging-state
  ;; The first render after mount has state that lags behind the node.
  (let [[state dispatch! process!] (async-state "")
        c (fn []
            [:input {:type "text"
                     :value @state
                     :on-change #(dispatch! (.. % -target -value))}])]
    (u/async
      (with-focused-input [c] (strict)
        (fn [node]
          (let [writes (spy-writes! node)]
            (type-text! node "a")
            (process!)
            (type-text! node "b")
            (type-text! node "c")
            ;; state = "a", node = "abc"
            (r/flush)
            (is (= "abc" (.-value node)))
            ;; state update for "b" arrives, "c" is still on the way
            (reset! state "ab")
            (r/flush)
            (is (= "abc" (.-value node)))
            (type-text! node "d")
            (r/flush)
            (process!)
            (r/flush)
            (p/do
              (change-check!)
              (is (= "abcd" @state (.-value node)) "no lost keys")
              (is (= [] @writes)))))))))
