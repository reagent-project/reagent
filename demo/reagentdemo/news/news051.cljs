(ns reagentdemo.news.news051
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [.' .!]]
            [reagent.debug :refer-macros [dbg println]]
            [reagentdemo.syntax :as s]
            [sitetools.core :as tools :refer [link]]
            [reagentdemo.common :as common :refer [demo-component]]))

(def url "news/news051.html")
(def title "News in 0.5.1")

(def ns-src (s/syntaxed "(ns example
  (:require [reagent.core :as r]))"))

(defn old-and-tired []
  [:ul
   [:li.foo [:a.bar "Link 1"]]
   [:li.foo [:a.bar "Link 2"]]])

(defn new-hotness []
  [:ul
   [:li.foo>a.bar "Link 1"]
   [:li.foo>a.bar "Link 2"]])

(defn upper-input []
  (let [v (r/atom "FOOBAR")]
    (fn []
      [:input {:type 'text :value @v
               :on-change #(reset! v (-> % .-target .-value
                                         clojure.string/upper-case))}])))


(defn main [{:keys [summary]}]
  [:div.reagent-demo
   [:h1 [link {:href url} title]]
   [:div.demo-text
    [:p "Reagent 0.5.1 contains a new convenient shortcut for nested
    elements, better error messages, new logic for maintaining cursor
    position in inputs, and some bug fixes and improvements."]
    
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
       whenever you made a change to something like this:"]

       [demo-component {:comp upper-input
                        :src (s/src-of [:upper-input])}]


       [:h2 "Other news"]

       [:ul
        [:li "React is updated to 0.13.3."]
        [:li "Better error messages. In particular, the current
        component path is now printed when an exception is thrown."]
        [:li "There is a new
        function, " [:code "reagent.core/force-update"] " that will
        make a component render immediately. It takes an optional
        second parameter that forces re-rendering of all children as
        well."]
        [:li "Some other bug fixes and performance tweaks."]]])]])

(tools/register-page url [#'main] title)
