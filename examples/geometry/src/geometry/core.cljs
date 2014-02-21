(ns geometry.core
  (:require 
   [reagent.core :as r]
   [geometry.components :as c]
   [geometry.geometry :as g]))

(enable-console-print!)

(def points 
  (r/atom
   {:p1 (g/point 100 100)
    :p2 (g/point 200 200)
    :p3 (g/point 100 200)
    :c (g/point 250 250)
    :p (g/point 250 300)}))

(defn move-point [svg-root p]
  (fn [x y]
    (let [bcr (-> svg-root
                  r/dom-node
                  .getBoundingClientRect)]
      (swap! points assoc p (g/point (- x (.-left bcr)) (- y (.-top bcr)))))))

(defn root [svg-root]
  (let [{:keys [p1 p2 p3 p c]} @points]
    [:g
     [c/triangle p1 p2 p3]
     [c/circle p c]
     [c/segment p c]
     [c/point {:on-drag (move-point svg-root :c)} c]
     [c/point {:on-drag (move-point svg-root :p)} p]
     [c/point {:on-drag (move-point svg-root :p1)} p1]
     [c/point {:on-drag (move-point svg-root :p2)} p2]
     [c/point {:on-drag (move-point svg-root :p3)} p3]]))

(defn main [{:keys [width height]}]
  [:svg 
   {:width (or width 800)
    :height (or height 600)
    :style {:border "1px solid black"}}
   [:text {:style {:-webkit-user-select "none"
                   :-moz-user-select "none"}
           :x 20 :y 20 :font-size 20}
    "The points are draggable"]
   [root (r/current-component)]])

(defn by-id [id]
  (.getElementById js/document id))

(defn ^:export run []
  (r/render-component 
   [main]
   (by-id "app")))
