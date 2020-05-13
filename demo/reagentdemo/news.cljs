(ns reagentdemo.news
  (:require [reagentdemo.news.anyargs :as anyargs]
            [reagentdemo.news.async :as async]
            [reagentdemo.news.undodemo :as undodemo]
            [reagentdemo.news.clockpost :as clock]
            [reagentdemo.news.news050 :as news050]
            [reagentdemo.news.news051 :as news051]
            [reagentdemo.news.news060 :as news060]
            [reagentdemo.news.news061 :as news061]
            [reagentdemo.news.news060rc :as news060rc]
            [reagentdemo.news.news060release :as news060r]
            [sitetools.core :as tools]))

(defn main []
  [:div
   [:div.reagent-demo
    [:h1 [:a {:href "https://github.com/reagent-project/reagent/blob/master/CHANGELOG.md"} "Check changelog for the latest releases"]]]

   [:div.reagent-demo
    [:h1 [:a {:href "https://github.com/reagent-project/reagent/blob/master/CHANGELOG.md"} "Reagent 1.0.0-alpha"]]]

   [:div.reagent-demo
    [:h1 [:a {:href "https://github.com/reagent-project/reagent/blob/master/CHANGELOG.md#0100-2020-03-06"} "Reagent 0.10.0"]]
    [:span "2020-03-06"]]

   [:div.reagent-demo
    [:h1 [:a {:href "https://github.com/reagent-project/reagent/blob/master/CHANGELOG.md#091-2020-01-15"} "Reagent 0.9.1"]]
    [:span "2020-01-15"]]

   [:div.reagent-demo
    [:h1 [:a {:href "https://github.com/reagent-project/reagent/blob/master/CHANGELOG.md#081-2018-05-15"} "Reagent 0.8.1"]]
    [:span "2018-05-15"]]

   [:div.reagent-demo
    [:h1 [:a {:href "https://github.com/reagent-project/reagent/blob/master/CHANGELOG.md#080-2018-04-19"} "Reagent 0.8.0"]]
    [:span "2018-04-19"]]

   [:div.reagent-demo
    [:h1 [:a {:href "https://github.com/reagent-project/reagent/blob/master/CHANGELOG.md#070-2017-06-27"} "Reagent 0.7.0"]]
    [:span "2017-06-27"]]

   [:div.reagent-demo
    [:h1 [:a {:href "https://github.com/reagent-project/reagent/blob/master/CHANGELOG.md#062-2017-05-19"} "Reagent 0.6.2"]]
    [:span "2017-05-19"]]

   [news061/main {:summary true}]
   [news060r/main {:summary true}]
   [news060rc/main {:summary true}]
   [news060/main {:summary true}]
   [news051/main {:summary true}]
   [news050/main {:summary true}]
   [clock/main {:summary true}]
   [anyargs/main {:summary true}]
   [async/main {:summary true}]
   [undodemo/main {:summary true}]
   [:div.reagent-demo
    [:h1 "Reagent 0.1.0"]
    [:span "2014-01-10"]]
   [:div.reagent-demo
    [:h1 "Reagent 0.0.2"]
    [:span "2013-12-17"]]])

(def url "/news/index.html")
(tools/register-page url [#'main] "News")
