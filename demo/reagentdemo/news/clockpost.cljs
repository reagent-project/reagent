(ns reagentdemo.news.clockpost
  (:require [reagent.core :as r :refer [atom]]
            [reagent.debug :refer-macros [dbg println]]
            [reagentdemo.syntax :refer-macros [get-source]]
            [reagentdemo.page :refer [title link page-map]]
            [reagentdemo.common :as common :refer [demo-component]]
            [reagentdemo.news.binaryclock :as binaryclock]))

(def funmap (-> "reagentdemo/news/binaryclock.cljs"
                get-source common/fun-map))
(def src-for (partial common/src-for funmap))

(defn fn-src [& parts]
  [demo-component {:src (src-for (vec parts))
                   :no-heading true}])

(defn main [{:keys [summary]}]
  (let [head "Binary clock"]
    
    [:div.reagent-demo
     [:h1 [link {:href main} head]]
     [title head]
     [:div.demo-text

      (when-not summary
        [:div
         ;; [demo-component {:comp binaryclock/main}]
         [binaryclock/main]
         [:p [:strong "Click to toggle 1/100th seconds."]]])

      [:h2 "A simple binary clock in Reagent"]

      [:p "some text"]
      
      (if summary
        [link {:href main
               :class 'news-read-more} "Read more"]
        [:div.demo-text

         [:p "We start with the basics: The clock is built out of
         cells, that are light or dark if the bit they correspond to
         is set."]

         [fn-src :cell]

         [:p "Cells are combined into columns of four bits, with a
         decimal digit at the bottom."]

         [fn-src :column]

         [:p "Columns are in turn combined into pairs:"]

         [fn-src :column-pair]

         [:p "We'll also need the legend on the left side:"]

         [fn-src :legend]

         [:p "We combine these element into a component that shows the
         legend, hours, minutes and seconds; and optionally 1/100
         seconds. It also responds to clicks."]

         [fn-src :clock]

         [:p "We also need to keep track of the time, and of the
         detail shown, in a Reagent atom. And a function to update the
         time."]

         [fn-src :clock-state :update-time]

         [:p "And finally we use the clock component like this:"]

         [fn-src :main]])]]))

(swap! page-map assoc
       "news/binary-clock.html" main)
