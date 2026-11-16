(ns reagentdemo.news
  "Archive: articles from the earlier Reagent website, kept at their old
  /news/ URLs."
  (:require [reagent.core :as r]
            [reagentdemo.news.anyargs :as anyargs]
            [reagentdemo.news.async :as async]
            [reagentdemo.news.undodemo :as undodemo]
            [reagentdemo.news.clockpost :as clock]
            [reagentdemo.news.news050 :as news050]
            [reagentdemo.news.news051 :as news051]
            [reagentdemo.news.news060 :as news060]
            [reagentdemo.news.news061 :as news061]
            [reagentdemo.news.news060rc :as news060rc]
            [reagentdemo.news.news060release :as news060r]
            [reagentdemo.docs :as docs]
            [sitetools.core :as tools :refer [link]]))

(def url "/news/")

(def articles
  "Newest first, as on the old news page. The 0.6.0 post says 2016-06-09,
  likely a typo: it came out after the 0.6.0-rc."
  [{:url news061/url :title news061/title :date "2017-03-11" :main #'news061/main}
   {:url news060r/url :title news060r/title :date "2016-06-09" :main #'news060r/main}
   {:url news060rc/url :title news060rc/title :date "2016-09-14" :main #'news060rc/main}
   {:url news060/url :title news060/title :date "2015-12-06" :main #'news060/main}
   {:url news051/url :title news051/title :date "2015-09-09" :main #'news051/main}
   {:url news050/url :title news050/title :date "2014-12-10" :main #'news050/main}
   {:url clock/url :title clock/title :date "2014-02-26" :main #'clock/main}
   {:url anyargs/url :title anyargs/title :date "2014-02-15" :main #'anyargs/main}
   {:url async/url :title async/title :date "2014-01-30" :main #'async/main}
   {:url undodemo/url :title undodemo/title :date "2014-01-19" :main #'undodemo/main}])

(r/defc archive-index []
  [:div.archive
   [:h1 "Archive"]
   [:p "Articles from the earlier Reagent website, kept for reference. They
   describe Reagent as it was when they were written, and the code may use
   APIs that have since changed. See the " [link {:href docs/guide-index} "guide"]
    " for current documentation and the "
    [:a {:href (str docs/github-blob "CHANGELOG.md")} "changelog"]
    " for all releases."]
   [:ul.archive-list
    (for [{:keys [url title date]} articles]
      ^{:key url}
      [:li [:span.archive-date date] [link {:href url} title]])]])

(r/defc archived-article [{:keys [date]} main]
  [:div.archive
   [:p.archive-notice
    [:strong "Archived article"] " from " date
    ". Reagent has changed since; see the "
    [link {:href docs/guide-index} "guide"] " for current documentation. "
    [link {:href url} "All archived articles"]]
   [main {}]])

(tools/register-page url [archive-index] "Archive")

(doseq [{:keys [url title main] :as article} articles]
  (tools/register-page url [archived-article article main] title))
