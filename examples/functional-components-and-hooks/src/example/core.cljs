(ns example.core
  (:require ["react" :as react]
            [clojure.string :as str]
            [reagent.core :as r]
            [reagent.dom.client :as rdomc]))

;; Same as simpleexample, but uses Hooks instead of Ratoms

(defn greeting [message]
  [:h1 message])

(defn clock [time-color]
  (let [[timer update-time] (react/useState (js/Date.))
        time-str (-> timer .toTimeString (str/split " ") first)]
    (react/useEffect
     (fn []
       (let [i (js/setInterval #(update-time (js/Date.)) 1000)]
         (fn []
           (js/clearInterval i)))))
    [:div.example-clock
     {:style {:color time-color}}
     time-str]))

(defn color-input [time-color update-time-color]
  [:div.color-input
   "Time color: "
   [:input {:type "text"
            :value time-color
            :on-change #(update-time-color (-> % .-target .-value))}]])

(defn simple-example []
  (let [[time-color update-time-color] (react/useState "#f34")]
    [:div
     [greeting "Hello world, it is now"]
     [clock time-color]
     [color-input time-color update-time-color]

     ;; Or with the default options you can create function components using :f> shortcut:
     #_#_#_
     [greeting "Hello world, it is now"]
     [:f> clock time-color]
     [:f> color-input time-color update-time-color]
     ]))

(def functional-compiler (r/create-compiler {:function-components true}))

(defonce react-root (delay (rdomc/create-root (.getElementById js/document "app"))))

(defn ^:export ^:dev/after-load run []
  (rdomc/render @react-root [simple-example] functional-compiler))
