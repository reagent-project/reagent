(ns reagenttest.e2e.inputs
  "Test page for the Playwright specs in e2e/. Each scenario renders one
  labelled controlled input with a data-testid, and a <pre> showing the
  current state (data-testid \"<id>-state\"). The :note and :known-issue
  texts are for trying the inputs by hand; the specs don't read them."
  (:require ["react" :as react]
            [clojure.string :as str]
            [reagent.core :as r]
            [reagent.dom.client :as rdomc]))

(defonce state (r/atom {}))

(defn- later
  "Run f on a later tick, like re-frame dispatch does."
  [f]
  (js/setTimeout f 0))

(defn- js-text-field
  "Like a text field from a JS component library: a React component that
  renders input-component itself, so Reagent's controlled input workaround
  isn't applied to it, unless input-component is Reagent's :input (like
  Material UI's inputComponent option)."
  [input-component]
  (r/adapt-react-class (fn [props] (react/createElement input-component props))))

(defn controlled-input
  [{:keys [id tag type transform async? style accept? note known-issue]
    :or {tag :input transform identity accept? (constantly true)}}]
  (let [v (get @state id "")
        update! (fn [x] (when (accept? x) (swap! state assoc id (transform x))))]
    [:div {:style {:margin "12px 0"}}
     [:label {:for id :style {:display "inline-block" :width 160}} id]
     [tag {:id id
           :data-testid id
           :type type
           :style style
           :value v
           :on-change (fn [e]
                        (let [x (.. e -target -value)]
                          (if async?
                            (later #(update! x))
                            (update! x))))}]
     [:pre {:data-testid (str id "-state") :style {:display "inline" :margin-left 8}}
      v]
     [:div {:style {:margin-left 160 :font-size "0.85em" :color "#555"}}
      note]
     (when known-issue
       [:div {:style {:margin-left 160 :font-size "0.85em" :color "#a40"}}
        [:strong "Known issue: "] known-issue])]))

(def mid-text
  "Type some text, move the cursor left with the arrow keys and type more: ")

(def scenarios
  [{:id "text-sync"
    :note (str mid-text "the new text goes at the cursor.")}
   {:id "email-sync" :type "email"
    :note (str mid-text "the new text goes at the cursor.")}
   {:id "number-sync" :type "number"
    :note (str mid-text "the new digits go at the cursor.")}
   {:id "email-lower-sync" :type "email" :transform str/lower-case
    :note "on-change lower-cases the value."
    :known-issue (str "typing an upper-case letter in the middle moves the cursor "
                      "to the end. Email inputs have no Selection API, so Reagent "
                      "can't restore the cursor after writing the lower-cased value.")}
   {:id "number-nozero-sync" :type "number" :transform #(str/replace % "0" "")
    :note "on-change drops zeros."
    :known-issue (str "typing 0 in the middle moves the cursor to the end. Number "
                      "inputs have no Selection API.")}
   {:id "upper-sync" :transform str/upper-case
    :note (str "on-change upper-cases the value. " mid-text
               "the cursor stays after the new text.")}
   {:id "textarea-sync" :tag :textarea
    :note (str mid-text "the new text goes at the cursor.")}
   {:id "textarea-upper-sync" :tag :textarea :transform str/upper-case
    :note (str "on-change upper-cases the value. " mid-text
               "the cursor stays after the new text.")}
   {:id "narrow-sync" :style {:width "80px"}
    :note "Type past the width: the cursor and the last letter stay visible."}
   {:id "narrow-upper-sync" :transform str/upper-case :style {:width "80px"}
    :note "on-change upper-cases the value. Type letters past the width."
    :known-issue (str "in Chrome the cursor and the last letter end up a few "
                      "pixels outside the input (#595).")}
   {:id "ime-sync"
    :note (str "Type with an IME, e.g. Korean (리에이전트) or Chinese pinyin "
               "(ni hao → 你好): no doubled or leftover letters.")}
   {:id "ime-async" :async? true
    :note (str "on-change updates the state on a later tick, like re-frame "
               "dispatch. Type with an IME: no doubled or leftover letters.")}
   {:id "text-async" :async? true
    :note (str "on-change updates the state on a later tick, like re-frame "
               "dispatch. Type fast: no keys are lost.")}
   {:id "reject-sync" :accept? #(not (re-find #"[0-9]" %))
    :note "on-change rejects values with digits: a typed digit disappears."
    :known-issue (str "keys typed very quickly after a digit (tens of "
                      "milliseconds) can be lost when the digit is removed.")}
   {:id "reject-async" :async? true :accept? #(not (re-find #"[0-9]" %))
    :note (str "Same as reject-sync, with the state updated on a later tick. "
               "The digit disappears after a short delay.")
    :known-issue (str "keys typed very quickly after a digit can be lost when "
                      "the digit is removed.")}
   {:id "upper-async" :async? true :transform str/upper-case
    :note (str "on-change upper-cases the value on a later tick. " mid-text
               "the cursor stays after the new text.")
    :known-issue (str "very fast typing loses keys: Reagent can't tell a "
                      "lagging upper-cased value from a new value set by the "
                      "app.")}
   {:id "js-text-sync" :tag (js-text-field "input")
    :note (str "The <input> is rendered by a JS component (like a component "
               "library), so Reagent's workaround isn't applied. " mid-text
               "the new text should go at the cursor.")
    :known-issue (str "the cursor jumps to the end after each key, fast typing "
                      "loses keys and IME input doubles letters (e.g. Korean "
                      "리에이전트 becomes ㄹ리링리에엥…), even with synchronous "
                      "state.")}
   {:id "js-reagent-text-sync"
    :tag (js-text-field (r/reactify-component (fn [props] [:input props])))
    :note (str "The same JS component, rendering Reagent's :input passed to it "
               "(like Material UI's inputComponent option), so the workaround "
               "applies. " mid-text "the new text goes at the cursor.")}])

(defn page []
  [:div
   [:h1 "Reagent controlled input e2e"]
   [:p "Each input shows the stored state on its right. The notes say what to
   try; known issues are expected to fail (the e2e specs mark them with
   test.fail)."]
   (for [s scenarios]
     ^{:key (:id s)} [controlled-input s])])

(defonce root (delay (rdomc/create-root (js/document.getElementById "app"))))

(defn ^:export init! []
  (rdomc/render @root [page]))
