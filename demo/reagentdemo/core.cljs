(ns reagentdemo.core
  (:require [sitetools.core :as tools :refer [link]]
            [reagentdemo.intro :as intro]
            [reagentdemo.news :as news]))

(def github {:href "https://github.com/reagent-project/reagent"})

(defn github-badge []
  [:a.github-badge
   github
   [:img {:style {:position "absolute" :top 0 :left 0 :border 0}
          :alt "Fork me on GitHub"
          :src "https://s3.amazonaws.com/github/ribbons/forkme_left_orange_ff7600.png"}]])

(def index-page "/index.html")
(def title "Minimalistic React for ClojureScript")

(tools/register-page index-page [#'intro/main] title)

(defn demo [& [test-component]]
  [:div
   [:div.nav>ul.nav
    [:li.brand [link {:href index-page} "Reagent:"]]
    [:li [link {:href index-page} "Intro"]]
    [:li [link {:href news/url} "News"]]
    [:li>a github "GitHub"]
    [:li [:a {:href "http://reagent-project.github.io/docs/master/"} "API"]]]
   [:div test-component]
   [tools/main-content]
   [github-badge]])

(defn init! [& [test-component]]
  (tools/start! {:body [#'demo test-component]
                 :title-prefix "Reagent: "
                 :css-infiles ["site/public/css/examples.css"
                               "site/public/css/main.css"]}))
