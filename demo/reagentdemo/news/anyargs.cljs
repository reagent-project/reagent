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

      [:h2 "If it looks like a function…"]

      [:p "Calling a component in Reagent looks a lot like a function
      call. Now it also " [:em "works"] " like one."]
      
      [:p "Before 0.4.0, component functions were always called with
      three arguments: a map of attributes, a vector of ”children”,
      and the current React component."]

      [:p "This was confusing, and an unnecessary limitation, so now
      component functions get exactly the same arguments you pass to
      them."]

      (if summary
        [link {:href main
               :class 'news-read-more} "Read more"]
        [:div.demo-text
         [:p "In other words, you can now do this:"]

         [demo-component {:comp say-hello
                          :src (src-for [:hello-component :say-hello])}]

         [:p "In the above example, it wouldn’t make any difference at
          all if " [:code "hello-component"] " had been called as a
          function, i.e with parentheses instead of brackets (except
          for performance, since components are cached between renders
          if the arguments to them don’t change)."]

         [:p "But there is one drawback: component function no longer
          receives the ”current component” as a parameter. Instead
          you’ll have to use "
          [:code "reagent.core/current-component"]
          " in order to get that. Beware that "
          [:code "current-component"] " is only valid in component
          functions, and must be called outside of e.g event handlers
          and " [:code "for"] " expressions, so it’s safest to always
          put the call at the top, as in " [:code "my-div"] " here:"]

         [demo-component {:comp call-my-div
                          :src (src-for [:nsr :my-div :call-my-div])}]

         [:p [:em "Note: "] [:code "r/props"] " and "
         [:code "r/children"] " correspond to React’s "
         [:code "this.props"] " and " [:code "this.props.children"] ",
         respectively. They may be convenient to use when wrapping
         native React components, since they follow the same
         conventions when interpreting the arguments given."]

         [:h2 "Other news in 0.4.0"]

         [:ul

          [:li "React has been updated to version 0.9.0."]

          [:li "You can now use any object that satisfies "
           [:code "ifn?"] " as a component function, and not just
           plain functions. That includes functions defined with "
           [:code "deftype"] ", " [:code "defrecord"] ", etc, as well
           as collections like maps."]

          [:li
           [:code "reagent.core/set-state"] " and "
           [:code "reagent.core/replace-state"] " are now implemented
           using an " [:code "reagent.core/atom"] ", and are
           consequently async."]

          [:li "Keys associated with items in a seq (e.g ”dynamic
           children” in React parlance) can now be specified with
           meta-data, as well as with a " [:code ":key"] " item in the
           first parameter as before. In other words, these two forms
           are now equivalent: " [:code "^{:key foo} [:li bar]"] "
           and " [:code "[:li {:key foo} bar]"] "."]

          [:li "Performance has been improved even further. For
           example, there is now practically no overhead for
           tracking derefs in components that don’t use "
           [:code "atom"] "s. Allocations and memory use have also
           been reduced."]

          [:li "Intro and examples have been tweaked a little to take
          advantage of the new calling conventions."]]

         [:h2 "New svg example"]

         [:p "There is also a new, elegant and simple "
          [:a geometry "example"] " of using svg with Reagent, written
          by " [:a jonase "Jonas Enlund"] ". It also shows how you can
          use Reagent’s new calling convensions, and looks like
          this:"]

         [demo-component {:comp geometry-example}]])]]))

(swap! page-map assoc
       "news/any-arguments.html" main)
