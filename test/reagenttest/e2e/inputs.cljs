(ns reagenttest.e2e.inputs
  "Test page for the Playwright specs in e2e/. Each scenario renders one
  labelled controlled input with a data-testid, and a <pre> showing the
  current state (data-testid \"<id>-state\")."
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [reagent.dom.client :as rdomc]))

(defonce state (r/atom {}))

(defn- later
  "Run f on a later tick, like re-frame dispatch does."
  [f]
  (js/setTimeout f 0))

(defn controlled-input
  [{:keys [id tag type transform async? style accept?]
    :or {tag :input transform identity accept? (constantly true)}}]
  (let [v (get @state id "")
        update! (fn [x] (when (accept? x) (swap! state assoc id (transform x))))]
    [:div {:style {:margin "8px 0"}}
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
      v]]))

(def scenarios
  [{:id "text-sync"}
   {:id "email-sync" :type "email"}
   {:id "number-sync" :type "number"}
   {:id "email-lower-sync" :type "email" :transform str/lower-case}
   {:id "number-nozero-sync" :type "number" :transform #(str/replace % "0" "")}
   {:id "upper-sync" :transform str/upper-case}
   {:id "textarea-sync" :tag :textarea}
   {:id "textarea-upper-sync" :tag :textarea :transform str/upper-case}
   {:id "narrow-sync" :style {:width "80px"}}
   {:id "narrow-upper-sync" :transform str/upper-case :style {:width "80px"}}
   {:id "ime-sync"}
   {:id "ime-async" :async? true}
   {:id "text-async" :async? true}
   {:id "reject-sync" :accept? #(not (re-find #"[0-9]" %))}
   {:id "reject-async" :async? true :accept? #(not (re-find #"[0-9]" %))}
   {:id "upper-async" :async? true :transform str/upper-case}])

(defn page []
  [:div
   [:h1 "Reagent controlled input e2e"]
   (for [s scenarios]
     ^{:key (:id s)} [controlled-input s])])

(defonce root (delay (rdomc/create-root (js/document.getElementById "app"))))

(defn ^:export init! []
  (rdomc/render @root [page]))
