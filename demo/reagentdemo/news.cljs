(ns reagentdemo.news
  (:require [reagentdemo.news.anyargs :as anyargs]
            [reagentdemo.news.async :as async]
            [reagentdemo.news.undodemo :as undodemo]
            [reagentdemo.news.clockpost :as clock]
            [reagentdemo.news.news050 :as news050]
            [sitetools.core :as tools :refer [dispatch link]]
            [secretary.core :as secretary :refer-macros [defroute]]))

(defn main []
  [:div
   [news050/main {:summary true}]
   [clock/main {:summary true}]
   [anyargs/main {:summary true}]
   [async/main {:summary true}]
   [undodemo/main {:summary true}]])

(defroute path "/news/index.html" []
  (dispatch [:set-content [#'main] "News"]))
(tools/reg-page (path))
