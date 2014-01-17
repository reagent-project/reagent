(ns reagentdemo.news
  (:require [reagent.core :as reagent :refer [atom]]))

(defn main []
  [:div
   [:h2 "This should become news"]])
