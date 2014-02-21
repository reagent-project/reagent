(ns geometry.components
  (:require [reagent.core :as r]
            [goog.events :as events]
            [geometry.geometry :refer [x y dist] :as g])
  (:import [goog.events EventType]))

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

(defn drag-move-fn [on-drag]
  (fn [evt]
    (on-drag (.-clientX evt) (.-clientY evt))))

(defn drag-end-fn [drag-move drag-end]
  (fn [evt]
    (events/unlisten js/window EventType.MOUSEMOVE drag-move)
    (events/unlisten js/window EventType.MOUSEUP @drag-end)))

(defn dragging [on-drag]
  (let [drag-move (drag-move-fn on-drag)
        drag-end-atom (atom nil)
        drag-end (drag-end-fn drag-move drag-end-atom)]
    (reset! drag-end-atom drag-end)
    (events/listen js/window EventType.MOUSEMOVE drag-move)
    (events/listen js/window EventType.MOUSEUP drag-end)))

(defn point [{:keys [on-drag]} p]
  [:circle 
   (merge point-defaults
          {:on-mouse-down #(dragging on-drag)
           :cx (x p)
           :cy (y p)})])

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
