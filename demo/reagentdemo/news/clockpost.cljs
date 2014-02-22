(ns reagentdemo.news.clockpost
  (:require [reagent.core :as r :refer [atom]]
            [reagent.debug :refer-macros [dbg println]]
            [reagentdemo.syntax :refer-macros [get-source]]
            [reagentdemo.page :refer [title link page-map]]
            [reagentdemo.common :as common :refer [demo-component]]
            [reagentdemo.news.binaryclock :as binaryclock]))

(def funmap (-> ::this get-source common/fun-map))
(def src-for (partial common/src-for funmap))


(defn main [{:keys [summary]}]
  (let [head "Binary clock"]
    
    [:div.reagent-demo
     [:h1 [link {:href main} head]]
     [title (str "Reagent 0.4.0: " head)]
     [:div.demo-text

      [:h2 "Binary clock"]

      [:p "x"]
      
      (if summary
        [link {:href main
               :class 'news-read-more} "Read more"]
        [:div.demo-text
         [:p "x"]

         [demo-component {:comp binaryclock/main}]])]]))

(swap! page-map assoc
       "news/binary-clock.html" main)
