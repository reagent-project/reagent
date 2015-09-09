(ns reagentdemo.news
  (:require [reagentdemo.news.anyargs :as anyargs]
            [reagentdemo.news.async :as async]
            [reagentdemo.news.undodemo :as undodemo]
            [reagentdemo.news.clockpost :as clock]
            [reagentdemo.news.news050 :as news050]
            [reagentdemo.news.news051 :as news051]))

(defn main []
  [:div
   [news051/main {:summary true}]
   [news050/main {:summary true}]
   [clock/main {:summary true}]
   [anyargs/main {:summary true}]
   [async/main {:summary true}]
   [undodemo/main {:summary true}]])
