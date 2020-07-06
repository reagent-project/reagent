(ns example.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            ["framer-motion" :refer [motion AnimatePresence]]))

(set! *warn-on-infer* true)

(defn animated []
  [:> (.-div motion)
   {:initial {:opacity 0}
    :animate {:opacity 1}
    :exit {:opacity 0}}
   "Animated element"])

(defn main []
  (let [show (r/atom false)]
    (fn []
      [:div
       [:> AnimatePresence
        {}
        (if @show
          [animated])]
       [:button
        {:on-click #(swap! show not)}
        "Toggle"]])))

(defn start []
  (rdom/render [main] (js/document.getElementById "app")))

(start)
