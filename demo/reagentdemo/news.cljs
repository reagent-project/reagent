(ns reagentdemo.news
  (:require [reagentdemo.news.anyargs :as anyargs]
            [reagentdemo.news.async :as async]
            [reagentdemo.news.undodemo :as undodemo]
            [reagentdemo.news.clockpost :as clock]
            [reagentdemo.news.news050 :as news050]
            [reagentdemo.news.news051 :as news051]
            [reagentdemo.news.news060 :as news060]
            [reagentdemo.news.news060release :as news060r]
            [sitetools.core :as tools]))

(defn main []
  [:div
   [news060r/main {:summary true}]
   [news060/main {:summary true}]
   [news051/main {:summary true}]
   [news050/main {:summary true}]
   [clock/main {:summary true}]
   [anyargs/main {:summary true}]
   [async/main {:summary true}]
   [undodemo/main {:summary true}]])

(def url "/news/index.html")
(tools/register-page url [#'main] "News")
