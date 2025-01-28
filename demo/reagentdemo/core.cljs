(ns reagentdemo.core
  (:require [reagent.core :as r]
            [sitetools.core :as tools :refer [link]]
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

(defn error-boundary [& children]
  (let [error (r/atom nil)]
    (r/create-class
      {:constructor (fn [this props])
       :component-did-catch (fn [this e info])
       :get-derived-state-from-error (fn [e]
                                       (reset! error e)
                                       #js {})
       :reagent-render (fn [& children]
                         (if @error
                           [:div
                            "Something went wrong."
                            [:input
                             {:type "button"
                              :on-click #(reset! error nil)
                              :value "Try again"}]]
                           (into [:<>] children)))})))

(defn demo [& [test-component]]
  [error-boundary
   [:div
    [:div.nav>ul.nav
     [:li.brand [link {:href index-page} "Reagent:"]]
     [:li [link {:href index-page} "Intro"]]
     [:li [link {:href news/url} "News"]]
     [:li>a github "GitHub"]
     [:li [:a {:href "http://reagent-project.github.io/docs/master/"} "API"]]]
    [:div test-component]
    [tools/main-content]
    [github-badge]]])

(defn init! [& [test-component]]
  (tools/start! {:body [#'demo test-component]
                 :title-prefix "Reagent: "
                 :css-infiles ["site/public/css/examples.css"
                               "site/public/css/main.css"]}))
