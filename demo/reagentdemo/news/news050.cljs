(ns reagentdemo.news.news050
  (:require [reagent.core :as r :refer [atom]]
            [reagent.interop :refer-macros [.' .!]]
            [reagent.debug :refer-macros [dbg println]]
            [reagentdemo.syntax :as s]
            [sitetools :as tools :refer [link]]
            [reagentdemo.common :as common :refer [demo-component]]
            [todomvc :as todomvc]))

(def url "news/news050.html")
(def title "News in 0.5.0-alpha")

(def ns-src (s/syntaxed "(ns example
  (:require [reagent.core :as r :refer [atom]]))"))

(defn input [prompt val]
  [:div
   prompt
   [:input {:value @val
            :on-change #(reset! val (.-target.value %))}]])

(defn name-edit [n]
  (let [{:keys [first-name last-name]} @n]
    [:div
     [:p "I'm editing " first-name " " last-name "."]
     
     [input "First name: " (r/wrap first-name
                                   swap! n assoc :first-name)]
     [input "Last name:  " (r/wrap last-name
                                   swap! n assoc :last-name)]]))

(defonce person (atom {:name
                       {:first-name "John" :last-name "Smith"}}))

(defn parent []
  [:div
   [:p "Current state: " (pr-str @person)]
   [name-edit (r/wrap (:name @person)
                      swap! person assoc :name)]])


(defn integration []
  [:div
   [:div.foo "Hello " [:strong "world"]]

   (r/create-element "div"
                     #js{:className "foo"}
                     "Hello "
                     (r/create-element "strong"
                                        #js{}
                                        "world"))

   (r/create-element "div"
                     #js{:className "foo"}
                     "Hello "
                     (r/as-element [:strong "world"]))

   [:div.foo "Hello " (r/create-element "strong"
                                        #js{}
                                        "world")]])

(def cel-link "http://facebook.github.io/react/docs/top-level-api.html#react.createelement")

(def figwheel-link "https://github.com/bhauman/lein-figwheel")

(defn main [{:keys [summary]}]
  [:div.reagent-demo
   [:h1 [link {:href url} title]]
   [:div.demo-text
    [:p "Lots of new features (and one breaking change) in Reagent
    0.5.0-alpha."]
    
    (if summary
      [link {:href url
             :class 'news-read-more} "Read more"]
      [:div.demo-text
       [:h2 "Splitting atoms"]

       [:p "Reagent now has a simple way to make reusable components
       that edit part of a parents state: "]

       [:p " The new " [:code "wrap"] "
       function combines two parts – a simple value, and a callback
       for when that value should change – into one value, that
       happens to look as an atom. "]

       [:p "The first argument to " [:code "wrap"] " should be the
       value that will be returned by " [:code "@the-result"] "."]

       [:p "The second argument should be a function, that will be
       passed the new value whenever the result is changed (with
       optional extra arguments to " [:code "wrap"] " prepended, as
       with " [:code "partial"] ")."]

       [:p "Usage can look something like this:"]

       [demo-component {:comp parent
                        :src [:pre ns-src
                              (s/src-of [:input :name-edit
                                         :person :parent])]}]

       [:p "Here, the " [:code "parent"] " component controls the
       global state, and delegates editing the name
       to " [:code "name-edit"] ". " [:code "name-edit"] " in turn
       delegates the actual input of first and last names
       to " [:code "input"] "."]

       [:p [:b "Note: "] "The result from " [:code "wrap"] " is just a
       simple and light-weight value, that happens to look like an
       atom – it doesn’t by itself trigger any re-renderings
       like " [:code "reagent.core/atom"] " does. That means that it
       is probably only useful to pass from one component to another,
       and that the callback function in the end must cause a ”real”
       atom to change."]


       [:h2 "Faster rendering"]

       [:p "Reagent used to wrap all ”native” React components in an
       extra Reagent component, in order to keep track of how deep in
       the component tree each component was (to make sure that
       un-necessary re-renderings were avoided)."]

       [:p "Now, this extra wrapper-component isn’t needed anymore,
       which means quite a bit faster generation of native React
       elements. This will be noticeable if you generate html strings,
       or if you animate a large number of components."]


       [:h2 "Simple React integration"]

       [:p "Since Reagent doesn't need those wrappers anymore it is
       also now easier to mix native React components with Reagent
       ones. There’s a new convenience
       function, " [:code "reagent.core/create-element"] ", that
       simply calls " [:a {:href
       cel-link} [:code "React.createElement"]] ". This,
       unsurprisingly, creates React elements, either from the result
       of " [:code "React.createClass"] " or html tag names."]

       [:p [:code "reagent.core/as-element"] " turns Reagent’s hiccup
       forms into React elements, that can be passed to ordinary React
       components. The combination of " [:code "create-element"] "
       and " [:code "as-element"] " allows mixing and matching of
       Reagent and React components."]

       [:p "For an example, here are four different ways to achieve
       the same thing:"]

       [demo-component {:comp integration
                        :src (s/src-of [:integration])}]


       [:h2 "More equality"]

       [:p "Reagent used to have a rather complicated way of
       determining when a component should be re-rendered in response
       to changing arguments. Now the rule is much simpler: a
       component will be re-rendered if the old and new arguments are
       not equal (i.e. they are compared with a
       simple " [:code "="] ")."]

       [:p [:strong "Note: "] "This is a breaking change! It means
       that you can no longer pass infinite " [:code "seq"] "s to a
       component."]

       [:h2 "React 0.12"]

       [:p "Reagent now comes with, and requires, React 0.12.1. To
       mirror the changes in API in React, some Reagent functions have
       gotten new names: "]

       [:ul
        [:li [:code "render-component"] " is now " [:code "render"]]
        [:li [:code "render-component-to-string"] " is now " [:code "render-to-string"]]
        [:li [:code "as-component"] " is now " [:code "as-element"]]]

       [:p "The old names still work, though."]

       [:p "There is also a new
       function, " [:code "render-to-static-markup"] ", that works
       just like render-to-string, except that it doesn’t add
       React-specific attributes."]


       [:h2 "Easier live-programming"]

       [:p "It is now easier than before to integrate Reagent with
       e.g. the rather excellent " [:a {:href
       figwheel-link} "figwheel"] ", since " [:code "render"] " now
       will cause the entire component tree to update (by-passing the
       equality checks)."] ])]])

(tools/register-page url [#'main] title)
