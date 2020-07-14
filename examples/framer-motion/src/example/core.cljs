(ns example.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            ["react" :as react]
            ["framer-motion" :refer [motion AnimatePresence useAnimation]]))

(set! *warn-on-infer* true)

(defn animated []
  [:> (.-div motion)
   {:initial {:opacity 0}
    :animate {:opacity 1}
    :exit {:opacity 0}}
   "Animated element"])

(defn use-animation-component []
  (let [controls (useAnimation)]
    (react/useEffect (fn []
                       (.start controls (fn [i]
                                          #js {;; :opacity 0
                                               :x 100
                                               :transition #js {:delay (* 0.3 i)}}))
                       (fn []))
                     #js [])
    [:ul
     (for [i (range 3)]
       [:> (.-li motion)
        {:key i
         :custom i
         :animate controls}
        "Item " i])]))

(defn main []
  (let [show (r/atom false)]
    (fn []
      [:div
       [:h1 "AnimatePresence"]
       [:> AnimatePresence
        {}
        (if @show
          [animated])]
       [:button
        {:on-click #(swap! show not)}
        "Toggle"]

       [:h1 "useAnimation"]
       [:f> use-animation-component]])))

(defn start []
  (rdom/render [main] (js/document.getElementById "app")))

(start)
