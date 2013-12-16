
(ns simpleexample
  (:require [cloact.core :as cloact :refer [atom partial]]
            [cloact.debug :refer-macros [dbg]]
            [clojure.string :as string]))

(def timer (atom (js/Date.)))
(def time-color (atom "#f34"))

(defn update-time [time]
  (js/setTimeout #(reset! time (js/Date.)) 100))

(defn greeting [props]
  [:h1 (:message props)])

(defn clock []
  (update-time timer)
  (let [time-str (-> @timer .toTimeString (string/split " ") first)]
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
  (cloact/render-component [simple-example]
                           (.-body js/document)))
