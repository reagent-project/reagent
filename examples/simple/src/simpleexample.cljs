
(ns simpleexample
  (:require [reagent.core :as reagent :refer [atom]]))

(def timer (atom (js/Date.)))
(def time-color (atom "#f34"))

(defn update-time [time]
  ;; Update the time every 1/10 second to be accurate...
  (js/setTimeout #(reset! time (js/Date.)) 100))

(defn greeting [message]
  [:h1 message])

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
   [greeting "Hello world, it is now"]
   [clock]
   [color-input]])

(defn ^:export run []
  (reagent/render-component (fn [] [simple-example])
                            (.-body js/document)))
