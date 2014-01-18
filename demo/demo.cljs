(ns demo
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as string]
            [reagentdemo.page :as rpage]
            [reagentdemo.news :as news]
            [reagentdemo.intro :as intro]
            [demoutil :as demoutil :refer-macros [get-source]]
            [reagentdemo.common :as common :refer [demo-component]]
            [reagent.debug :refer-macros [dbg println]]))

(def page rpage/page)

(defn link [props children]
  (apply vector :a (assoc props
                     :on-click (if rpage/history
                                 (fn [e]
                                   (.preventDefault e)
                                   (reset! page (:href props)))
                                 identity))
         children))

(defn github-badge []
  [:a.github-badge
   {:href "https://github.com/holmsand/reagent"}
   [:img {:style {:position "absolute" :top 0 :left 0 :border 0}
          :src "https://s3.amazonaws.com/github/ribbons/forkme_left_orange_ff7600.png"
          :alt "Fork me on GitHub"}]])

(defn demo []
  [:div
   [:div
    [:ul
     [:li [link {:href "news.html"} "News"]]
     [:li [link {:href "index.html"} "Intro"]]]]
   (case (dbg @page)
     "index.html" [intro/main]
     "news.html" [news/main]
     [intro/main])
   [github-badge]])

(defn ^:export mountdemo []
  (reagent/render-component [demo] (.-body js/document)))

(defn ^:export genpage []
  (reagent/render-component-to-string [demo]))
