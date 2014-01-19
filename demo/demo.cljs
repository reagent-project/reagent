(ns demo
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as string]
            [reagentdemo.page :as rpage]
            [reagentdemo.news :as news]
            [reagentdemo.intro :as intro]
            [reagentdemo.common :as common :refer [demo-component]]
            [reagent.debug :refer-macros [dbg println]]))

(def page rpage/page)
(def title-atom (atom "Reagent: Minimalistic React for ClojureScript"))

(defn prefix [href]
  (let [depth (-> #"/" (re-seq @page) count)
        pref (->> "../" (repeat depth) (apply str))]
    (str pref href)))

(defn link [props children]
  (apply vector :a (assoc props
                     :href (-> props :href prefix)
                     :on-click (if rpage/history
                                 (fn [e]
                                   (.preventDefault e)
                                   (reset! page (:href props)))
                                 identity))
         children))

(defn github-badge []
  [:a.github-badge
   {:href "https://github.com/holmsand/reagent"}
   [:img {:style {:position "absolute" :top 0 :left 0 :border 0}
          :src "https://s3.amazonaws.com/github/ribbons/forkme_left_orange_ff7600.png"
          :alt "Fork me on GitHub"}]])

(defn demo []
  [:div
   [:div
    [:ul
     [:li [link {:href "news/index.html"} "News"]]
     [:li [link {:href "index.html"} "Intro"]]]]
   (case (dbg @page)
     "index.html" [intro/main]
     "news/index.html" [news/main]
     "news/cloact-reagent-undo-demo.html" [news/main]
     "news/" [news/main]
     [intro/main])
   [github-badge]])

(defn ^:export mountdemo [p]
  (when p (reset! page p))
  (reagent/render-component [demo] (.-body js/document)))

(defn gen-page [p timestamp]
  (reset! page p)
  (let [body (reagent/render-component-to-string [demo])
        title @title-atom]
    (str "<!doctype html>
<html>
  <head>
    <meta charset='utf-8'>
    <title>" title "</title>
    <meta name='viewport' content='width=device-width, initial-scale=1.0' />
    <link rel='stylesheet' href='" (prefix "assets/demo.css") timestamp "'>
  </head>
  <body>
    " body "
    <script type='text/javascript'
      src='" (prefix "assets/demo.js") timestamp "'></script>
    <script type='text/javascript'>
      setTimeout(function() {demo.mountdemo('" p "')}, 200);
    </script>
  </body>
</html>")))

(defn ^:export genpages []
  (let [timestamp (str "?" (.now js/Date))]
    (->> ["index.html" "news/index.html"
          "news/cloact-reagent-undo-demo.html"]
         (map #(vector % (gen-page % timestamp)))
         (into {})
         clj->js)))
