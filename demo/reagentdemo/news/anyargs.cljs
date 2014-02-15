(ns reagentdemo.news.anyargs
  (:require [reagent.core :as r :refer [atom]]
            [reagent.debug :refer-macros [dbg println]]
            [reagentdemo.syntax :refer-macros [get-source]]
            [reagentdemo.page :refer [title link page-map]]
            [reagentdemo.common :as common :refer [demo-component]]
            [geometry.core :as geometry]))

(def funmap (-> ::this get-source common/fun-map))
(def src-for (partial common/src-for funmap))

(defn hello-component [name]
  [:p "Hello, " name "!"])

(defn say-hello []
  [hello-component "world"])

(defn geometry-example []
  [geometry/main {:width "100%" :height 500}])

(defn my-div []
  (let [this (r/current-component)]
    (into [:div.custom (r/props this)]
          (r/children this))))

(defn call-my-div []
  [:div
   [my-div "Some text."]
   [my-div {:style {:font-weight 'bold}}
    [:p "Some other text in bold."]]])

(defn main [{:keys [summary]}]
  (let [head "All arguments allowed"
        geometry {:href "https://github.com/holmsand/reagent/tree/master/examples/geometry"}
        jonase {:href "https://github.com/jonase"}]
    
    [:div.reagent-demo
     [:h1 [link {:href main} head]]
     [title (str "Reagent 0.4.0: " head)]
     [:div.demo-text
      
      [:p "Reagent 0.4.0 lets component functions take any kinds of
      arguments, and not just maps and vectors matching Hiccup’s
      calling conventions. Before 0.4.0, component functions were
      always called with three arguments: a map (called props), a
      vector of ”children”, and a the current component (a.k.a
      ”this”)."]

      [:p "This was confusing, so now component functions get exactly
      the same arguments you pass to them."]

      (if summary
        [link {:href main
               :class 'news-read-more} "Read more"]
        [:div.demo-text
         [:p "In other words, you can now do this:"]

         [demo-component {:comp say-hello
                          :src (src-for [:hello-component :say-hello])}]

         [:p "In the above example, it wouldn’t make any difference at
         all if " [:code "hello-component"] " had been called as a
         function, i.e with parenthesis instead of brackets (except
         for performance, since components are cached between renders
         if the arguments to them don’t change)."]

         [:p "But there is one drawback: component function no longer
         receives the ”current component” as a parameter. Instead
         you’ll have to use "
         [:code "reagent.core/current-component"] " in order to get
         that. Beware that " [:code "current-component"] " must be
         used outside of e.g event handlers and " [:code "for"] "
         expressions, so it’s safest to always put it at the top, like
         this:"]

         [demo-component {:comp call-my-div
                          :src (src-for [:nsr :my-div :call-my-div])}]

         [:p "There is also a new, elegant and simple "
          [:a geometry "example"] " of using svg with Reagent, written
          by " [:a jonase "Jonas Enlund"] ". It also shows how you can
          use Reagent’s new calling convensions, and looks like
          this:"]

         [demo-component {:comp geometry-example}]])]]))

(swap! page-map assoc
       "news/any-arguments.html" main)
