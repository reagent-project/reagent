(ns reagentdemo.core
  (:require [reagent.core :as r]
            [clojure.string :as string]
            [sitetools.core :as tools :refer [dispatch link]]
            [reagentdemo.common :as common :refer [demo-component]]
            [reagentdemo.intro :as intro]
            [reagentdemo.news :as news]
            [reagent.debug :refer-macros [dbg println]]))

(def test-results (r/atom nil))

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


(defn demo []
  [:div
   [:div.nav>ul.nav
    [:li.brand [link {:href index-page} "Reagent:"]]
    [:li [link {:href index-page} "Intro"]]
    [:li [link {:href news/url} "News"]]
    [:li>a github "GitHub"]]
   [:div @test-results]
   [tools/main-content]
   [github-badge]])

(defn init! []
  (tools/start! {:body [#'demo]
                 :title-prefix "Reagent: "
                 :css-infiles ["site/public/css/examples.css"
                               "site/public/css/main.css"]}))

(init!)
