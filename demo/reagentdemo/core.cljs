(ns reagentdemo.core
  (:require [reagent.core :as r]
            [sitetools.core :as tools :refer [link]]
            [reagentdemo.intro :as intro]
            [reagentdemo.news :as news]))

(def github {:href "https://github.com/reagent-project/reagent"})

(defn github-badge []
  [:a.github-badge
   github
   [:img {:width 149
          :height 149
          :style {:position "absolute" :top 0 :left 0 :border 0}
          :alt "Fork me on GitHub"
          :src "https://github.blog/wp-content/uploads/2008/12/forkme_left_orange_ff7600.png"}]])

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

(def test-component (r/atom nil))

(defn demo []
  [error-boundary
   [:div
    [:div.nav>ul.nav
     [:li.brand [link {:href index-page} "Reagent:"]]
     [:li [link {:href index-page} "Intro"]]
     [:li [link {:href news/url} "News"]]
     [:li>a github "GitHub"]
     [:li [:a {:href "http://reagent-project.github.io/docs/master/"} "API"]]]
    (when-let [c @test-component]
      [:div c])
    [tools/main-content]
    [github-badge]]])

(defn init! []
  (tools/start! {:body [#'demo]
                 :title-prefix "Reagent: "
                 :css-infiles ["site/public/css/examples.css"
                               "site/public/css/main.css"]}))
