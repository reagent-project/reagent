
(ns simpleexample
  (:require [reagent.core :as reagent :refer [atom]]))

(def timer (atom (js/Date.)))
(def time-color (atom "#f34"))

(defn update-time [time]
  ;; Update the time every 1/10 second to be accurate...
  (js/setTimeout #(reset! time (js/Date.)) 100))

(defn greeting [props]
  [:h1 (:message props)])

(defn clock []
  (update-time timer)
  (let [time-str (-> @timer .toTimeString (clojure.string/split " ") first)]
    [:div.example-clock
     {:style {:color @time-color}}
     time-str]))

(defn color-input []
  [:div.color-input
   "Time color: "
   [:input {:type "text"
            :value @time-color
            :on-change #(reset! time-color (-> % .-target .-value))}]])

(defn simple-example []
  [:div
   [greeting {:message "Hello world, it is now"}]
   [clock]
   [color-input]])

(defn ^:export run []
  (reagent/render-component [simple-example]
                            (.-body js/document)))
