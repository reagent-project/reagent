(ns reagentdemo.core
  (:require [reagent.core :as r]
            [sitetools.core :as tools :refer [link]]
            [reagentdemo.intro :as intro]
            [reagentdemo.news] ; registers the old release note pages
            [reagentdemo.docs :as docs])
  (:require-macros [reagentdemo.docs :refer [inline-file]]))

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

;; Set by reagentdemo.dev, which a hot reload of this ns doesn't re-run
;; (e.g. reagentdemo.docs is ^:dev/always and reloads its dependents).
(defonce test-component (r/atom nil))

(def logo-svg (inline-file "logo/logo.svg"))

;;; Theme: an explicit choice is stored in localStorage and set as
;;; data-theme on <html> (see the inline script in the page <head>);
;;; without one the CSS follows prefers-color-scheme.

(defn current-theme []
  (or (.. js/document -documentElement -dataset -theme)
      (if (.-matches (js/matchMedia "(prefers-color-scheme: dark)"))
        "dark"
        "light")))

(defonce theme (r/atom nil))

(defn toggle-theme! []
  (let [t (if (= "dark" (or @theme (current-theme))) "light" "dark")]
    (set! (.. js/document -documentElement -dataset -theme) t)
    (try (.setItem js/localStorage "theme" t) (catch :default _))
    (reset! theme t)))

(def sun-icon
  [:svg {:viewBox "0 0 24 24" :fill "none" :stroke "currentColor"
         :stroke-width 2 :stroke-linecap "round"}
   [:circle {:cx 12 :cy 12 :r 4}]
   [:path {:d "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"}]])

(def moon-icon
  [:svg {:viewBox "0 0 24 24" :fill "none" :stroke "currentColor"
         :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
   [:path {:d "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"}]])

(r/defc theme-toggle []
  ;; Server render has no theme; the client renders the real state.
  (let [dark? (= "dark" (or @theme (when r/is-client (current-theme))))]
    [:button.theme-toggle {:on-click toggle-theme!
                           :title (if dark? "Switch to light theme" "Switch to dark theme")
                           :aria-label "Toggle dark theme"}
     (if dark? sun-icon moon-icon)]))

(r/defc header []
  [:header.site-header
   [:div.site-header-inner
    [link {:href index-page :class "brand"}
     [:span.brand-logo {:dangerouslySetInnerHTML (r/unsafe-html logo-svg)}]
     "Reagent"]
    [:nav.site-nav
     [link {:href docs/guide-index} "Guide"]
     [link {:href docs/api-index} "API"]
     [:a {:href (str docs/github-blob "CHANGELOG.md")} "Changelog"]
     [:a {:href docs/github} "GitHub"]]
    [docs/search-box]
    [theme-toggle]]])

(defn demo []
  [error-boundary
   [:div.site
    [header]
    [:main [tools/main-content]]
    ;; Guide and API pages render the footer inside their content column.
    (when-not (docs/docs-page? (docs/current-path))
      [docs/site-footer])
    (when-let [c @test-component]
      [:div c])]])

(defn init! []
  (tools/start! {:body [#'demo]
                 :title-prefix "Reagent: "
                 :css-infiles ["site/public/css/examples.css"
                               "site/public/css/main.css"
                               "site/public/css/site.css"]}))
