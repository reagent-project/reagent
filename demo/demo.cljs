(ns demo
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as string]
            [reagentdemo.common :as common
             :refer [demo-component page link reverse-page-map prefix]]
            [reagentdemo.intro :as intro]
            [reagentdemo.news :as news]
            [reagent.debug :refer-macros [dbg println]]))

(common/set-page-map {:index ["index.html" [intro/main]]
                      :news ["news/index.html" [news/main]]
                      :undo-demo ["news/cloact-reagent-undo-demo.html"
                                  [news/main]]})

(defn github-badge []
  [:a.github-badge
   {:href "https://github.com/holmsand/reagent"}
   [:img {:style {:position "absolute" :top 0 :left 0 :border 0}
          :src "https://s3.amazonaws.com/github/ribbons/forkme_left_orange_ff7600.png"
          :alt "Fork me on GitHub"}]])

(defn demo []
  [:div
   [:div.nav
    [:ul.nav
     [:li.brand [link {:href :index} "Reagent:"]]
     [:li [link {:href :index} "Introduction"]]
     [:li [link {:href :news} "News"]]]]
   (let [p @page
         [_ comp] (get @reverse-page-map p
                       (:index @common/page-map))]
     comp)
   [github-badge]])

(defn ^:export mountdemo [p]
  (when p (reset! page p))
  (reagent/render-component [demo] (.-body js/document)))

(defn gen-page [p timestamp]
  (reset! page p)
  (let [body (reagent/render-component-to-string [demo])
        title @common/title-atom]
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
    (->> (keys @reverse-page-map)
         (map #(vector % (gen-page % timestamp)))
         (into {})
         clj->js)))
