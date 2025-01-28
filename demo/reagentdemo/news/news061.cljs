(ns reagentdemo.news.news061
  (:require [reagentdemo.syntax :as s]
            [sitetools.core :as tools :refer [link]]))

(def url "/news/news061.html")
(def title "Reagent 0.6.1")

(def ns-src (s/syntaxed "(ns example.core
  (:require [reagent.core :as r]))"))

(def changelog
  "https://github.com/reagent-project/reagent/blob/master/CHANGELOG.md")

(defn abstract []
  [:div.demo-text
   [:p
    "Reagent 0.6.1 has a new version of React (15.4.0), and it fixes a bug with
    " [:code ":ref"] " attributes on " [:code "input"] " elements: " [:a {:href "https://github.com/reagent-project/reagent/issues/259"} "#259"] "."]])

(defn main [{:keys [summary]}]
  [:div.reagent-demo
   [:h1
    [link {:href url} title]]
   [:span "2017-03-11"]
   [:div
    [abstract]]])

(tools/register-page url [#'main] title)
