(ns demo
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.interop :as i :refer-macros [.' .!]]
            [clojure.string :as string]
            [sitetools :as tools :refer [link]]
            [reagentdemo.common :as common :refer [demo-component]]
            [reagentdemo.intro :as intro]
            [reagentdemo.news :as news]
            [reagent.debug :refer-macros [dbg println]]))

(i/import-react)

(def test-results-comp (atom nil))

(def github {:href "https://github.com/reagent-project/reagent"})

(defn github-badge []
  [:a.github-badge
   github
   [:img {:style {:position "absolute" :top 0 :left 0 :border 0}
          :alt "Fork me on GitHub"
          :src "https://s3.amazonaws.com/github/ribbons/forkme_left_orange_ff7600.png"}]])

(def index-page "index.html")
(def news-page "news/index.html")

(tools/register-page index-page
                     (fn [] [intro/main])
                     "Reagent: Minimalistic React for ClojureScript")
(tools/register-page news-page
                     (fn [] [news/main])
                     "Reagent news")

(defn demo []
  [:div
   [:div.nav
    [:ul.nav
     [:li.brand [link {:href index-page} "Reagent:"]]
     [:li [link {:href index-page} "Intro"]]
     [:li [link {:href news-page} "News"]]
     [:li [:a github "GitHub"]]]]
   (when @test-results-comp [@test-results-comp])
   [tools/page-content]
   [github-badge]])


(defn start! [{:keys [test-results]}]
  (reset! test-results-comp test-results)
  (tools/start! {:body (fn [] [demo])
                 :css-infiles ["site/public/css/main.css"
                               "site/public/css/examples.css"]}))
