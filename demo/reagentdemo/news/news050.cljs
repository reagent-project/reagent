(ns reagentdemo.news.news050
  (:require [reagent.core :as r]
            [reagent.interop :refer-macros [.' .!]]
            [reagent.debug :refer-macros [dbg println]]
            [reagentdemo.syntax :as s]
            [sitetools.core :as tools :refer [link]]
            [reagentdemo.common :as common :refer [demo-component]]))

(def url "news/news050.html")
(def title "News in 0.5.0")

(def new-in-alpha [:strong "New since 0.5.0-alpha: "])

(def ns-src (s/syntaxed "(ns example
  (:require [reagent.core :as r]))"))

(def cel-link "http://facebook.github.io/react/docs/top-level-api.html#react.createelement")

(def figwheel-link "https://github.com/bhauman/lein-figwheel")



(defonce person (r/atom {:name
                         {:first-name "John" :last-name "Smith"}}))

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

(defn parent []
  [:div
   [:p "Current state: " (pr-str @person)]
   [name-edit (r/wrap (:name @person)
                      swap! person assoc :name)]])

(defn cursor-name-edit [n]
  (let [{:keys [first-name last-name]} @n]
    [:div
     [:p "I'm editing " first-name " " last-name "."]

     [input "First name: " (r/cursor n [:first-name])]
     [input "Last name:  " (r/cursor n [:last-name])]]))

(defn cursor-parent []
  [:div
   [:p "Current state: " (pr-str @person)]
   [cursor-name-edit (r/cursor person [:name])]])

(defn person-get-set
  ([k] (get-in @person k))
  ([k v] (swap! person assoc-in k v)))

(defn get-set-parent []
  [:div
   [:p "Current state: " (pr-str @person)]
   [cursor-name-edit (r/cursor person-get-set [:name])]])

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


(def div-adapter (r/adapt-react-class "div"))

(defn adapted []
  [div-adapter {:class "foo"}
   "Hello " [:strong "world"]])


(defn exported [props]
  [:div "Hi, " (:name props)])

(def react-comp (r/reactify-component exported))

(defn could-be-jsx []
  (r/create-element react-comp #js{:name "world"}))


(defn main [{:keys [summary]}]
  [:div.reagent-demo
   [:h1 [link {:href url} title]]
   [:div.demo-text
    [:p "Reagent 0.5.0 has automatic importing of React.js, two kinds
    of cursors, better integration of native React components, better
    performance, easier integration with e.g Figwheel, and more."]
    
    (if summary
      [link {:href url
             :class 'news-read-more} "Read more"]
      [:div.demo-text
       [:h2 "A new way of importing React"]

       [:p new-in-alpha "Reagent now takes advantage of
       ClojureScript’s new way of packaging JavaScript dependencies.
       That means that you no longer have to include React in your
       HTML, nor should you use " [:code ":preamble"] ". Instead,
       Reagent depends on the " [:code "cljsjs/react"] " library."]

       [:p "If you want to use another version of React, you can do
       that in two ways. In both cases you’ll have to
       exclude " [:code "cljsjs/react"] " by using
       e.g " [:code "[reagent \"0.5.0-alpha3\" :exclusions [cljsjs/react]]"]
        " in the " [:code ":dependencies"] " section of your "
        [:code "project.clj"] "."]

       [:p "You can then add e.g " [:code "[cljsjs/react-with-addons
       \"0.12.2-4\"]"] " as a dependency. Or you can add a file
       named " [:code "cljsjs/react.cljs"] ", containing
       just " [:code "(ns cljsjs.react)"] ", to your project – and then
       import React in some other way."]

       [:p "Reagent now requires ClojureScript 0.0-2816 or later."]


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
                              (s/src-of [:person :input :name-edit
                                         :parent])]}]

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

       [:h2 "Cursors"]

       [:p new-in-alpha "Reagent has another way of isolating part of
       a data structure in an atom: " [:code "reagent.core/cursor"] ".
       Using the same state as in the previous example, usage now looks
       like this:"]

       [demo-component {:comp cursor-parent
                        :src (s/src-of [:cursor-name-edit
                                        :cursor-parent])}]

       [:p new-in-alpha "Cursors can now also be generalized to use
       any transformation of data from and to a source atom (or many
       atoms, for that matter). To use that, you pass a function
       to " [:code "cursor"] " instead of an atom, as in this
       example:"]

       [demo-component {:comp cursor-parent
                        :src (s/src-of [:person-get-set
                                        :get-set-parent])}]

       [:p "The function passed to " [:code "cursor"] " will be called
       with one argument to get data (it is passed the key, i.e the
       second argument to " [:code "cursor"] "), and two arguments
       when the cursor is changed (then it is passed the key and the
       new value)."]

       [:p "The getter function can reference one or many Reagent
       atoms (or other cursors). If the cursor is used in a component
       the getter function will re-run to change the value of the
       cursor just like a Reagent component does."]

       [:h3 "Values and references"]

       [:p "So what’s the difference between wraps and cursors? Why
       have both?"]

       [:p "A " [:code "wrap"] " is just a value that happens to look
       like an " [:code "atom"] ". It doesn’t change unless you tell
       it to. It is a very lightweight combination of value and a
       callback to back-propagate changes to the value. It relies only
       on Reagent’s equality test
       in " [:code ":should-component-update"] " to avoid unnecessary
       re-rendering."]

       [:p "A " [:code "cursor"] ", on the other hand, will always be
       up-to-date with the value of the source atom. In other words,
       it acts a reference to part of the value of the source.
       Components that " [:code "deref"] " cursors are re-rendered
       automatically, in exactly the same way as if
       they " [:code "deref"] " a normal Reagent atom (unnecessary
       re-rendering is avoided by checking if the cursor's value has
       changed using " [:code "identical?"] ")."]
       

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

       [:p new-in-alpha "If you don't need/want this kind of low-level
       control over interaction with javascript React, you can also
       use the new function " [:code "adapt-react-class"] ", that will
       take any React class, and turn it into something that can be
       called from Reagent directly. The example from above would then
       become:"]

       [demo-component {:comp adapted
                        :src (s/src-of [:div-adapter :adapted])}]

       [:p new-in-alpha "You can also do the opposite: call Reagent
       components from JavaScript React (for example from JSX). For
       this purpose, you'd use another adapter
       – " [:code "reactify-component"] " – like this:"]

       [demo-component {:comp could-be-jsx
                        :src (s/src-of [:exported :react-comp
                                        :could-be-jsx])}]

       [:p "The " [:code "exported"] " component will be called with a
       single argument: the React " [:code "props"] ", converted to a
       ClojureScript " [:code "map"] "."]


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

       [:p "Reagent now comes with, and requires, React 0.12.2. To
       mirror the changes in API in React, some Reagent functions have
       gotten new names: "]

       [:ul
        [:li [:code "render-component"] " is now " [:code "render"]]
        [:li [:code "render-component-to-string"] " is
        now " [:code "render-to-string"]]
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
       equality checks)."]

       [:p new-in-alpha "All the examples in the Reagent repo now uses
       figwheel."]])]])

(tools/register-page url [#'main] title)
