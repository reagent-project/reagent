(ns reagentdemo.news.binaryclock
  (:require [reagent.core :as r :refer [atom]]
            [reagent.debug :refer-macros [dbg println]]))

(defn cell [n bit]
  [:div.clock.cell {:class (if (bit-test n bit)
                             'light
                             'dark)}])

(defn column [n]
  [:div.clock.col
   [cell n 3]
   [cell n 2]
   [cell n 1]
   [cell n 0]
   [:div.clock.cell n]])

(defn column-pair [n]
  [:div.clock.colpair
   [column (quot n 10)]
   [column (mod n 10)]])

(defn legend [& numbers]
  (into [:div.clock.col.legend]
        (map (partial vector :div.clock.cell) numbers)))

(defn clock [time show-ms toggle-ms]
  [:div.clock.main {:on-click toggle-ms
                    :class (when show-ms 'wide)}
   [legend 8 4 2 1]
   [column-pair (.getHours time)]
   [column-pair (.getMinutes time)]
   [column-pair (.getSeconds time)]
   (if show-ms
     [column-pair (-> (.getMilliseconds time)
                      (quot 10))])])

(def clock-state (atom {:show-ms false
                        :time (js/Date.)}))

(defn update-time []
  (swap! clock-state assoc :time (js/Date.)))

(defn now []
  (.now js/Date))

(defn timing-wrapper [f]
  (let [start-time (atom nil)
        render-time (atom nil)
        start #(reset! start-time (now))
        stop #(reset! render-time (- (now) @start-time))
        timed-f (with-meta f
                  {:component-will-mount start
                   :component-will-update start
                   :component-did-mount stop
                   :component-did-update stop})]
    (fn [& args]
      [:div
       [:p [:em "render time: " @render-time "ms"]]
       (into [timed-f] args)])))

(def clock-with-timing (timing-wrapper clock))

(defn main []
  (let [{:keys [show-ms time]} @clock-state]
    (if show-ms
      (r/next-tick update-time)
      (js/setTimeout update-time 333))
    [clock-with-timing time show-ms
     #(swap! clock-state update-in [:show-ms] not)]))
