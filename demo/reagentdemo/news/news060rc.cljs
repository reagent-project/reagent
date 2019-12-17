(ns reagentdemo.news.news060rc
  (:require [reagent.core :as r]
            [reagentdemo.syntax :as s]
            [sitetools.core :as tools :refer [link]]
            [reagentdemo.news.news060 :as news060]
            [reagentdemo.common :as common :refer [demo-component]]))

(def url "/news/news060-rc.html")
(def title "Reagent 0.6.0-rc")

(def ns-src (s/syntaxed "(ns example.core
  (:require [reagent.core :as r]))"))

(defn mixed []
  [:div
   "Symbols are " 'ok " as well as " :keywords "."])

(def some-atom (r/atom 0))

(defn confusion-avoided []
  [:div "This is some atom: " some-atom])


(defn main [{:keys [summary]}]
  [:div.reagent-demo
   [:h1 [link {:href url} title]]
   [:span "2016-09-14"]
   [:div.demo-text
    [:p "Reagent 0.6.0-rc has been given a lot of testing, a new
    version of React (15.1.0), bug fixing and some small general
    improvements since 0.6.0-alpha. It has one new feature: general
    ClojureScript objects can now be used anywhere in markup
    content."]

    (if summary
      [link {:href url :class 'news-read-more} "Read more"]
      [:div.demo-text
       [:section.demo-text
        [:p "See " [link {:href news060/url} "this
        article"] " for more information about Reagent 0.6.0."]

        [:h2 "Generalized markup"]

        [:p "Symbols and keywords can now be used in markup content
        like this: "]

        [demo-component {:comp mixed
                         :src (s/src-of [:mixed])}]

        [:p "This makes content conversions behave the same as in
        attributes, where symbols and keywords have been supported
        before. "]

        [:p "But mainly it avoids confusing error messages when you
        happen to drop an arbitrary ClojureScript object into the
        markup, like this: "]

        [demo-component {:comp confusion-avoided
                         :src (s/src-of [:some-atom
                                         :confusion-avoided])}]

        [:p "This may not be particularly useful, but it is at least a
        lot better than getting a quite confusing error message from
        React, that no longer accepts unknown objects…"]

        [:p "Any object hat satisfies IPrintWithWriter is allowed, and
        is converted to a string using " [:code "pr-str" "."]]]])]])


(tools/register-page url [#'main] title)
