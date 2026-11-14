(ns reagentdemo.core
  (:require [reagent.core :as r]
            [sitetools.core :as tools :refer [link]]
            [reagentdemo.intro :as intro]
            [reagentdemo.news :as news]
            [reagentdemo.docs :as docs])
  (:require-macros [reagentdemo.docs :refer [inline-file]]))

(def github "https://github.com/reagent-project/reagent")

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

(def logo-svg (inline-file "logo/logo.svg"))

(r/defc header []
  [:header.site-header
   [:div.site-header-inner
    [link {:href index-page :class "brand"}
     [:span.brand-logo {:dangerouslySetInnerHTML (r/unsafe-html logo-svg)}]
     "Reagent"]
    [:nav.site-nav
     [link {:href docs/guide-index} "Guide"]
     [link {:href docs/api-index} "API"]
     [:a {:href (str github "/blob/master/CHANGELOG.md")} "Changelog"]
     [:a {:href github} "GitHub"]]
    [docs/search-box]]])

(r/defc footer []
  [:footer.site-footer
   [:a {:href github} "GitHub"] " · "
   [:a {:href "https://cljdoc.org/d/reagent/reagent/"} "cljdoc"] " · "
   [:a {:href "https://clojurians.slack.com/messages/reagent/"} "#reagent on Clojurians Slack"] " · "
   [link {:href news/url} "Old release notes"]])

(defn demo []
  [error-boundary
   [:div.site
    [header]
    [:main [tools/main-content]]
    [footer]
    (when-let [c @test-component]
      [:div c])]])

(defn init! []
  (tools/start! {:body [#'demo]
                 :title-prefix "Reagent: "
                 :css-infiles ["site/public/css/examples.css"
                               "site/public/css/main.css"
                               "site/public/css/site.css"]}))
