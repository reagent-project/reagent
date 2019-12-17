(ns reagentdemo.news.news051
  (:require [reagent.core :as r]
            [reagentdemo.syntax :as s]
            [sitetools.core :as tools :refer [link]]
            [reagentdemo.common :as common :refer [demo-component]]
            [clojure.string :as string]))

(def url "/news/news051.html")
(def title "News in 0.5.1")

(def ns-src (s/syntaxed "(ns example.core
  (:require [reagent.core :as r]))"))

(defn old-and-tired []
  [:ul
   [:li.foo [:a.bar "Text 1"]]
   [:li.foo [:a.bar "Text 2"]]])

(defn new-hotness []
  [:ul
   [:li.foo>a.bar "Text 1"]
   [:li.foo>a.bar "Text 2"]])

(def upper-value (r/atom "FOOBAR"))

(defn upper-input []
  [:div
   [:p "Value is: " @upper-value]
   [:input {:type 'text :value @upper-value
            :on-change #(reset! upper-value
                                (-> % .-target .-value string/upper-case))}]])


(defn main [{:keys [summary]}]
  [:div.reagent-demo
   [:h1 [link {:href url} title]]
   [:span "2015-09-09"]
   [:div.demo-text
    [:p "Reagent 0.5.1 contains a new convenient shortcut for nested
    elements, better error messages, new logic for maintaining cursor
    position in inputs, a new version of React, and some bug fixes and
    improvements."]

    (if summary
      [link {:href url :class 'news-read-more} "Read more"]
      [:div.demo-text
       [:h2 "New syntax for nested elements"]

       [:p "The ”Hiccup syntax” used in Reagent now supports defining
       nested elements using ”>” as a separator in keywords. This is
       probably easier to show than to explain…"]

       [:p "So, instead of doing this: "]

       [demo-component {:comp old-and-tired
                        :src (s/src-of [:old-and-tired])}]

       [:p "you can now do this: "]

       [demo-component {:comp new-hotness
                        :src (s/src-of [:new-hotness])}]

       [:p "with identical results, thus saving several square
       brackets from an untimely death."]


       [:h2 "Keeping position"]

       [:p "Reagent now tries harder to maintain cursor position in
       text inputs, even when the value of the input is transformed in
       code."]

       [:p "Previously, the cursor would jump to the end of the text
       whenever you made a change in the middle of the text in
       something like this:"]

       [demo-component {:comp upper-input
                        :src [:pre ns-src
                              (s/src-of [:upper-value :upper-input])]}]


       [:h2 "Other news"]

       [:ul
        [:li "React is updated to 0.13.3."]

        [:li "A bit better error messages. In particular, the current
        component path is now printed when an exception is thrown."]

        [:li "There is a new
        function, " [:code "reagent.core/force-update"] " that will
        make a component render immediately. It takes an optional
        second parameter that forces re-rendering of every child
        component as well."]

        [:li "Calling the result
        of " [:code "reagent.core/create-class"] " as a function is
        now deprecated. Use Hiccup syntax instead."]

        [:li "Some other bug fixes and performance tweaks."]]])]])

(tools/register-page url [#'main] title)
