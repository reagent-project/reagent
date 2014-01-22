(ns demo
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as string]
            [reagentdemo.page :as page :refer [page-map page link prefix]]
            [reagentdemo.common :as common :refer [demo-component]]
            [reagentdemo.intro :as intro]
            [reagentdemo.news :as news]
            [reagent.debug :refer-macros [dbg println]]))

(swap! page-map assoc
       "index.html" intro/main
       "news/index.html" news/main)

(def github {:href "https://github.com/holmsand/reagent"})

(defn github-badge []
  [:a.github-badge
   github
   [:img {:style {:position "absolute" :top 0 :left 0 :border 0}
          :alt "Fork me on GitHub"
          :src "https://s3.amazonaws.com/github/ribbons/forkme_left_orange_ff7600.png"}]])

(defn demo []
  [:div
   [:div.nav
    [:ul.nav
     [:li.brand [link {:href intro/main} "Reagent:"]]
     [:li [link {:href intro/main} "Intro"]]
     [:li [link {:href news/main} "News"]]
     [:li [:a github "GitHub"]]]]
   (let [comp (get @page-map @page intro/main)]
     [comp])
   [github-badge]])

(defn ^:export mountdemo [p]
  (when p (reset! page p))
  (reagent/render-component [demo] (.-body js/document)))

(defn gen-page [p timestamp]
  (reset! page p)
  (let [body (reagent/render-component-to-string [demo])
        title @page/title-atom]
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
    (->> (keys @page-map)
         (map #(vector % (gen-page % timestamp)))
         (into {})
         clj->js)))
