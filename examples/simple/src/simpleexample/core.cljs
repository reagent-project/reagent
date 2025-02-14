(ns simpleexample.core
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [reagent.dom.client :as rdomc]))

(defonce timer (r/atom (js/Date.)))

(defonce time-color (r/atom "#f34"))

(defonce time-updater (js/setInterval
                       #(reset! timer (js/Date.)) 1000))

(defn greeting [message]
  [:h1 message])

(defn clock []
  (let [time-str (-> @timer .toTimeString (str/split " ") first)]
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

(defonce root (delay (rdomc/create-root (.getElementById js/document "app"))))

(defn ^:export run []
  (rdomc/render @root [simple-example]))
