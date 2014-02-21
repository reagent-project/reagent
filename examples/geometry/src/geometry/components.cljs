(ns geometry.components
  (:require [reagent.core :as r]
            [geometry.geometry :refer [x y dist] :as g]))

(def point-defaults
  {:stroke "black"
   :stroke-width 2
   :fill "blue"
   :r 5})

(def segment-defaults
  {:stroke "black"
   :stroke-width 2})

(def circle-defaults
  {:fill "rgba(255,0,0,0.1)"
   :stroke "black"
   :stroke-width 2})

(defn point [p]
  [:circle
   (merge point-defaults
          {:cx (x p) :cy (y p)})])

(defn drag [mouse-info p]
  (when (:mouse-down? @mouse-info)
    (reset! p (g/point (:x @mouse-info)
                       (:y @mouse-info)))
    (r/next-tick
     (fn []
       (drag mouse-info p)))))

(defn draggable-point [p mouse-info]
  [:circle 
   (merge point-defaults
          {:on-mouse-down #(do 
                             (swap! mouse-info assoc :mouse-down? true)
                             (drag mouse-info p))
           :cx (x @p)
           :cy (y @p)})])

(defn segment [from to]
  [:line 
   (merge segment-defaults
          {:x1 (x from) :y1 (y from)
           :x2 (x to) :y2 (y to)})])

(defn triangle [a b c]
  [:g 
   [segment a b]
   [segment b c]
   [segment c a]])

(defn circle [c r]
  [:circle
   (merge circle-defaults
          {:cx (x c)
           :cy (y c)
           :r (dist c r)})])
