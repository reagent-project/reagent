(ns reagentdemo.news.news060release
  (:require [reagentdemo.syntax :as s]
            [sitetools.core :as tools :refer [link]]
            [reagentdemo.news.news060 :as news060]
            [reagentdemo.news.news060rc :as news060rc]))

(def url "/news/news060.html")
(def title "Reagent 0.6.0")

(def ns-src (s/syntaxed "(ns example.core
  (:require [reagent.core :as r]))"))

(def changelog
  "https://github.com/reagent-project/reagent/blob/master/CHANGELOG.md")

(defn abstract []
  [:div.demo-text
   [:p
    "Reagent 0.6.0 has a new version of React (15.2.1), and a few
    bug fixes. Otherwise it is identical to 0.6.0-rc."]])

(defn story []
  [:div.demo-text
   [:p
    "See " [link {:href news060/url} "this story"]
    " for much more information about Reagent 0.6.0."]
   [:p
    "You can also have a look at the "
    [link {:href news060rc/url} "news in 0.6.0-rc"]
    " and the " [link {:href changelog} "change log"]
    "."]])

(defn main [{:keys [summary]}]
  [:div.reagent-demo
   [:h1
    [link {:href url} title]]
   [:span "2016-06-09"]
   [:div
    [abstract]
    (if summary
      [link {:href url :class 'news-read-more} "Read more"]
      [:section.demo-text
       [story]])]])

(tools/register-page url [#'main] title)
