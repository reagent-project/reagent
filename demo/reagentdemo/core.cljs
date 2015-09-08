(ns reagentdemo.core
  (:require [reagent.core :as r]
            [reagent.interop :as i :refer-macros [.' .!]]
            [clojure.string :as string]
            [sitetools.core :as tools :refer [dispatch link]]
            [secretary.core :as secretary :refer-macros [defroute]]
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

;; (def index-page "index.html")
;; (def news-page "news/index.html")

;; (tools/register-page index-page [#'intro/main]
;;                      "Reagent: Minimalistic React for ClojureScript")
;; (tools/register-page news-page [#'news/main]
;;                      "Reagent news")

(def title "Minimalistic React for ClojureScript")

(defroute "/" []
  (dispatch [:set-content [#'intro/main] title]))
(defroute main-page "/index.html" []
  (dispatch [:set-content [#'intro/main] title]))
(tools/reg-page (main-page))


(defn demo []
  [:div
   [:div.nav
    [:ul.nav
     [:li.brand [link {:href (main-page)} "Reagent:"]]
     [:li [link {:href (main-page)} "Intro"]]
     [:li [link {:href (news/path)} "News"]]
     [:li [:a github "GitHub"]]]]
   @test-results
   [tools/page-content]
   [github-badge]])

(defn init! []
  (tools/start! {:body [#'demo]
                 :title-prefix "Reagent: "
                 :css-infiles ["site/public/css/examples.css"
                               "site/public/css/main.css"]}))

(init!)
