(ns geometry.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [geometry.components :as c]
            [geometry.geometry :as g]))

(enable-console-print!)

(defonce points
  (r/atom
   {:p1 (g/point 100 100)
    :p2 (g/point 200 200)
    :p3 (g/point 100 200)
    :c (g/point 250 250)
    :p (g/point 250 300)}))

(defonce slider
  (r/atom
   {:handle (g/point 500 50)
    :history []}))

(defn record-state [_ _ _ s]
  (swap! slider (fn [{:keys [history] :as coll}]
                  (assoc coll :history (conj history s)))))

(defn start-recording-history []
  (let [history (:history @slider)]
    (add-watch points :record record-state)))

(defn stop-recording-history []
  (remove-watch points :record))

(add-watch points :record record-state)

(defn get-bcr [svg-root]
  (-> svg-root
      rdom/dom-node
      .getBoundingClientRect))

(defn move-point [svg-root p]
  (fn [x y]
    (let [bcr (get-bcr svg-root)]
      (swap! points assoc p (g/point (- x (.-left bcr)) (- y (.-top bcr)))))))

(defn move-slider [svg-root p]
  (fn [x y]
    (let [new-x (-> (- x (.-left (get-bcr svg-root)))
                    (min 500)
                    (max 100))
          position (/ (- new-x 100)
                      (- 500 100))
          history (:history @slider)
          history-points (nth history (int (* (dec (count history)) position)) nil)]
      (swap! slider assoc p (g/point new-x 50))
      (if history-points
        (reset! points history-points)))))

(defn root [svg-root]
  (let [{:keys [p1 p2 p3 p c]} @points]
    [:g
     [c/triangle p1 p2 p3]
     [c/circle p c]
     [c/segment p c]
     [c/segment (g/point 100 50) (g/point 500 50)]
     [c/rect {:on-drag (move-slider svg-root :handle)
              :on-start stop-recording-history
              :on-end start-recording-history} (:handle @slider)]
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
    "The points are draggable and the slider controls history"]
   [root (r/current-component)]])

(defn by-id [id]
  (.getElementById js/document id))

(defn ^:export run []
  (rdom/render [main] (by-id "app")))
