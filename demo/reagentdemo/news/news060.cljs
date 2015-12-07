(ns reagentdemo.news.news060
  (:require [reagent.core :as r]
            [reagent.debug :refer-macros [dbg println]]
            [reagentdemo.syntax :as s]
            [sitetools.core :as tools :refer [link]]
            [reagentdemo.common :as common :refer [demo-component]]))

(def url "/news/news060-alpha.html")
(def title "News in 0.6.0-alpha")

(def ns-src (s/syntaxed "(ns example.core
  (:require [reagent.core :as r]))"))

(defonce app-state (r/atom {:people
                            {1 {:first-name "John"
                                :last-name "Smith"}
                             2 {:first-name "Maggie"
                                :last-name "Johnson"}}}))

(defn people []
  (:people @app-state))

(defn person-keys []
  (-> @(r/track people)
      keys
      sort))

(defn person [id]
  (-> @(r/track people)
      (get id)))

(defn name-comp [id]
  (let [p @(r/track person id)]
    [:li
     (:first-name p) " " (:last-name p)]))

(defn name-list []
  (let [ids @(r/track person-keys)]
    [:ul
     (for [i ids]
       ^{:key i} [name-comp i])]))

(defn main [{:keys [summary]}]
  [:div.reagent-demo
   [:h1 [link {:href url} title]]
   [:div.demo-text
    [:p "Reagent 0.6.0-alpha contains new reactivity helpers, better
    integration with native React components, a new version of React,
    and much more. "]

    (if summary
      [link {:href url :class 'news-read-more} "Read more"]
      [:div.demo-text
       [:h2 "Use any function as a reactive value"]

       [:p [:code "reagent.core/track"] " takes a function, and
       optional arguments for that function, and gives a
       derefable (i.e ”atom-like”) value, containing whatever is
       returned by that function. If the tracked function depends on a
       Reagent atom, the function is called again whenever that atom
       changes – just like a Reagent component function. If the value
       returned by " [:code "track"] " is used in a component, the
       component is re-rendered when the value returned by the
       function changes. "]

       [:p "In other words, " [:code "@(r/track foo x)"] " gives the
       same result as " [:code "(foo x)"] " – but in the first case,
       foo is only called again when the atom(s) it depends on
       changes."]

       [:p "Here's an example: "]

       [demo-component {:comp name-list
                        :src (s/src-of [:app-state
                                        :people
                                        :person-keys
                                        :person
                                        :name-comp
                                        :name-list])}]

       [:p "Here, the " [:code "name-list"] " component will only be
       re-rendered if the keys of the " [:code ":people"] " map
       changes. Every " [:code "name-comp"] " only renders again when
       needed, etc."]

       [:p "Use of " [:code "track"] " can improve performance in
       three ways:" ]

       [:ul
        [:li "It can be used as a cache for an expensive function,
        that is automatically updated if that function depends on Reagent
        atoms (or other tracks, cursors, etc)."]

        [:li "It can also be used to limit the number of times a
        component is re-rendered. The user of " [:code "track"] " is
        only updated when the function’s result changes. In other
        words, you can use track as a kind of generalized, read-only
        cursor."]

        [:li "Every use of " [:code "track"] " with the same arguments
        will only result in one execution of the function. E.g the two
        uses of " [:code "@(r/track people)"] " in the example above
        will only result in one call to the " [:code "people"] "
        function (both initially, and when the state atom changes)."]]

       ])]])

(tools/register-page url [#'main] title)
