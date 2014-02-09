(ns core
  (:require 
   [reagent.core :as r]
   [components :as c]
   [geometry :as g]))

(enable-console-print!)

;; "Global" mouse events
(def mouse-info 
  (r/atom {:x 0
           :y 0
           :mouse-down? false}))

(defn on-mouse-move [evt]
  (swap! mouse-info assoc :x (.-clientX evt) :y (.-clientY evt)))

(defn on-mouse-up [evt]
  (swap! mouse-info assoc :mouse-down? false))

(defn on-mouse-down [evt]
  (swap! mouse-info assoc :mouse-down? true))

(def p1 (r/atom (g/point 100 100)))
(def p2 (r/atom (g/point 200 200)))
(def p3 (r/atom (g/point 100 200)))

(defn root []
  [:g
   [c/triangle @p1 @p2 @p3]
   [c/draggable-point p1 mouse-info]
   [c/draggable-point p2 mouse-info]
   [c/draggable-point p3 mouse-info]])

(defn by-id [id]
  (.getElementById js/document id))

(defn ^:export run []
  (r/render-component 
   [:svg {:on-mouse-down on-mouse-down
          :on-mouse-up on-mouse-up
          :on-mouse-move on-mouse-move
          :width 400
          :height 400
          :style {:border "1px solid black"}}
    [:text {:style {:-webkit-user-select "none"} 
            :x 20 :y 20 :font-size 20}
     "The corners are draggable"]
    [root]]  
   (by-id "app")))

