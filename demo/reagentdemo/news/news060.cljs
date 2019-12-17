(ns reagentdemo.news.news060
  (:require [reagent.core :as r]
            [reagentdemo.syntax :as s]
            [sitetools.core :as tools :refer [link]]
            [reagentdemo.common :as common :refer [demo-component]]))

(def url "/news/news060-alpha.html")
(def title "News in 0.6.0-alpha")

(def ns-src (s/syntaxed "(ns example.core
  (:require [reagent.core :as r]))"))

(defonce app-state (r/atom {:people
                            {1 {:name "John Smith"}
                             2 {:name "Maggie Johnson"}}}))

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
     (:name p)]))

(defn name-list []
  (let [ids @(r/track person-keys)]
    [:ul
     (for [i ids]
       ^{:key i} [name-comp i])]))


(defn log-app-state []
  (prn @app-state))

(def --space nil)

#_(defonce logger (r/track! log-app-state))

#_(r/dispose! logger)


(when-not (exists? js/document)
  ;; Add no-op methods for node.js
  (set! js/global.document
        #js{:addEventListener identity
            :removeEventListener identity}))

(defn mouse-pos-comp []
  (r/with-let [pointer (r/atom nil)
               handler #(swap! pointer assoc
                               :x (.-pageX %)
                               :y (.-pageY %))
               _ (.addEventListener js/document "mousemove" handler)]
    [:div
     "Pointer moved to: "
     (str @pointer)]
    (finally
      (.removeEventListener js/document "mousemove" handler))))

(defn mouse-pos []
  (r/with-let [pointer (r/atom nil)
               handler #(swap! pointer assoc
                               :x (.-pageX %)
                               :y (.-pageY %))
               _ (.addEventListener js/document "mousemove" handler)]
    @pointer
    (finally
      (.removeEventListener js/document "mousemove" handler))))

(defn tracked-pos []
  [:div
   "Pointer moved to: "
   (str @(r/track mouse-pos))])


(defn event-handler [state [event-name id value]]
  (case event-name
    :set-name   (assoc-in state [:people id :name]
                          value)
    :add-person (let [new-key (->> state :people keys (apply max) inc)]
                  (assoc-in state [:people new-key]
                            {:name ""}))
    state))

(defn emit [e]
  ;; (js/console.log "Handling event" (str e))
  (r/rswap! app-state event-handler e))

(defn name-edit [id]
  (let [p @(r/track person id)]
    [:div
     [:input {:value (:name p)
              :on-change #(emit [:set-name id (.-target.value %)])}]]))

(defn edit-fields []
  (let [ids @(r/track person-keys)]
    [:div
     [name-list]
     (for [i ids]
       ^{:key i} [name-edit i])
     [:input {:type 'button
              :value "Add person"
              :on-click #(emit [:add-person])}]]))


(defn cursor-example []
  (let [first-person (r/cursor app-state [:people 1])]
    [:p "A person: " (:name @first-person)]))


(defn main [{:keys [summary]}]
  [:div.reagent-demo
   [:h1 [link {:href url} title]]
   [:span "2015-12-06"]
   [:div.demo-text
    [:p "Reagent 0.6.0-alpha contains new reactivity helpers, better
    integration with native React components, a new version of
    React (0.14.3), new React dependencies ("[:code "react-dom"]"
    and "[:code "react-dom-server"]"), better performance, and much
    more. "]

    [:p "This is a quite big release, so it probably contains a fair
    amount of bugs as well…"]

    (if summary
      [link {:href url :class 'news-read-more} "Read more"]
      [:div.demo-text
       [:section.demo-text
        [:h2 "Breaking changes"]

        [:ul
         [:li "Reagent now depends on "[:code "cljsjs/react-dom"]"
         and "[:code "cljsjs/react-dom-server"]" (see below)."]

         [:li "The javascript interop macros "[:code ".'"]"
         and "[:code ".!"]", in the "[:code "reagent.interop"]"
         namespace are now called "[:code "$"]" and "[:code "$!"]"
         respectively (the old names clashed with bootstrapped
         ClojureScript)."]

         [:li "Reactions, i.e "[:code "cursor"]" called with a
         function, "[:code "reagent.ratom/reaction"]", "[:code "reagent.ratom/run!"]"
         and "[:code "reagent.ratom/make-reaction"]" are now lazy and
         executed asynchronously. Previously, reactions used to
         execute immediately whenever the atoms they depended on
         changed. This could cause performance issues in code with
         expensive reactions and frequent updates to state. However,
         this change may break existing code that depends on the
         timing of side-effects from running
         reactions. "[:code "flush"]" can be used to force outstanding
         reactions to run at a given time."]

         [:li "Reactions now only trigger updates of dependent
         components and other reactions if they produce a new result,
         compared with "[:code "="]".
         Previously, "[:code "identical?"]" was used."]

         [:li [:code "next-tick"]" is now guaranteed to execute its
         argument before the next render (more on that below.)"]]]


       [:h2 "track: Use any function as a reactive value"]

       [:p [:code "reagent.core/track"] " takes a function, and
       optional arguments for that function, and gives a
       derefable (i.e ”atom-like”) value, containing whatever is
       returned by that function. If the tracked function depends on a
       Reagent atom, it is called again whenever that atom changes –
       just like a Reagent component function. If the value returned
       by " [:code "track"] " is used in a component, the component is
       re-rendered when the value returned by the function changes. "]

       [:p "In other words, " [:code "@(r/track foo x)"] " gives the
       same result as " [:code "(foo x)"] " – but in the first case,
       foo is only called again when the atom(s) it depends on
       changes."]

       [:p "Here's an example: "]

       [demo-component {:comp name-list
                        :src [:pre ns-src
                              (s/src-of [:app-state
                                         :people
                                         :person-keys
                                         :person
                                         :name-comp
                                         :name-list])]}]

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

       [:p "If you've been using "[:code "reagent.ratom/reaction"]"
       etc, "[:code "track"]" should be quite familiar. The main
       difference is that "[:code "track"]" uses named functions and
       variables, rather than depending on closures, and that you
       don’t have to manage their creation manually (since tracks are
       automatically cached and reused)."]

       [:p [:b "Note: "] "The first argument to "[:code "track"]"
       should be a named function, i.e not an anonymous one. Also,
       beware of lazy data sequences: don’t use deref (i.e ”@”) with
       the "[:code "for"]" macro, unless wrapped
       in "[:code "doall"]" (just like in Reagent components). "]


       [:h2 "track!"]

       [:p [:code "track!"]" is another new function. It works just
       like "[:code "track"]", except that the function passed is
       invoked immediately, and continues to be invoked whenever any
       atoms used within it changes."]

       [:p "For example, given this function:"]

       [demo-component {:src (s/src-of [:log-app-state])}]

       [:p "you could use " [:code "(defonce logger (r/track!
       log-app-state))"]" to monitor changes
       to "[:code "app-state"]". "[:code "log-app-state"]" would
       continue to run until you stop it, using "[:code "(r/dispose!
       logger)"]"."]


       [:h2 "with-let: Handling destruction"]

       [:p "Reagent now has a new way of writing components that need
       to do something when they are no longer around:
       the "[:code "with-let"]" macro. It looks just
       like " [:code "let"] " – but the bindings only execute once,
       and it takes an optional " [:code "finally"] " clause, that
       runs when the component is no longer rendered."]

       [:p "For example: here's a component that sets up an event
       listener for mouse moves, and stops listening when the
       component is removed."]

       [demo-component {:comp mouse-pos-comp
                        :src (s/src-of [:mouse-pos-comp])}]

       [:p "The same thing could of course be achieved with React
       lifecycle methods, but that would be a lot more verbose."]

       [:p [:code "with-let"]" can also be combined
       with "[:code "track"]" (and other reactive contexts). For
       example, the component above could be written as: "]

       [demo-component {:comp tracked-pos
                        :src (s/src-of [:mouse-pos
                                        :tracked-pos])}]

       [:p "The " [:code "finally"] " clause will run
       when " [:code "mouse-pos"] " is no longer tracked anywhere, i.e
       in this case when " [:code "tracked-pos"] "is unmounted."]

       [:p [:code "with-let"]" can also generally be used instead of
       returning functions from components that keep local state, and
       may be a bit easier to read."]


       [:section.demo-text
        [:h2 "Event handling with rswap!"]

        [:p [:code "rswap!"] " is another new function in 0.6.0. It
        works like standard "[:code "swap!"]" except that it"]

        [:ul
         [:li "always returns nil"]
         [:li "allows recursive applications of "[:code "rswap!"]" on the same
         atom."]]

        [:p "That makes "[:code "rswap!"]" especially suited for event
        handling."]

        [:p "Here’s an example that uses event handling
        with "[:code "rswap!"]" to edit the data introduced in the
        section about "[:code "track"]" above:"]

        [demo-component {:comp edit-fields
                         :src (s/src-of [:event-handler
                                         :emit
                                         :name-edit
                                         :edit-fields])}]

        [:p "All events are passed through the "[:code "emit"]"
        function, consisting of a trivial application
        of "[:code "rswap!"]" and some optional logging. This is the
        only place where application state actually changes – the rest
        is pure functions."]

        [:p "The actual event handling is done
        in "[:code "event-handler"]", which takes state and event as
        parameters, and returns a new state (events are represented by
        vectors here, with an event name in the first position)."]

        [:p "All the UI components have to do is then just to return
        some markup, and set up routing of events through the "[:code "emit"]"
        function. "]

        [:p "This architecture basically divides the application into
        two logical functions: "]

        [:ul
         [:li "The first takes state and an event as input, and returns
         the next state."]
         [:li "The other takes state as input, and returns a UI
         definition."]]

        [:p "This simple application could probably just as well use
        the common "[:code "swap!"]" instead of "[:code "rswap!"]",
        but using "[:code "swap!"]" in React’s event handlers may
        trigger warnings due to unexpected return values, and may
        cause severe headaches if an event handler called by "[:code "emit"]"
        itself emits a new event (that would result in lost
        events, and much confusion)."]

        [:p "For a more structured version of a similar approach, see
        the excellent "[:a
        {:href "https://github.com/Day8/re-frame"} "re-frame"]"
        framework."]]

       [:section.demo-text
        [:h2 "New React version and new namespaces"]

        [:p "Reagent now depends on React version 0.14.3. React itself
        is now split into three parts, with separate packages for
        browser specific code, and HTML generation respectively."]

        [:p "To reflect that, two new namespaces have been introduced
        in Reagent as well: "[:code "reagent.dom"]"
        and "[:code "reagent.dom.server"]". They contain functions
        that used to be in "[:code "reagent.core"]". "]

        [:p [:code "reagent.dom"]" contains: "]

        [:ul
         [:li [:code "render"]]
         [:li [:code "unmount-component-at-node"]]
         [:li [:code "dom-node"]]
         [:li [:code "force-update-all"]]]

        [:p [:code "reagent.dom.server"]" contains: "]

        [:ul
         [:li [:code "render-to-string"]]
         [:li [:code "render-to-static-markup"]]]

        [:p "These functions are still available
        in "[:code "reagent.core"]" in this release (for backward
        compatibility reasons), but they may be deprecated in the
        future."]

        [:p "The changes in React also mean that if you specify the
        React version to use in your project.clj,
        with "[:code "cljsjs/react"]" in the "[:code ":dependencies"]"
        section, you now have to specify "[:code "cljsjs/react-dom"]"
        and "[:code "cljsjs/react-dom-server"]" instead."]]

       [:section.demo-text
        [:h2 "Better interop with native React"]

        [:p "The output of "[:code "create-class"]" can now be used
        directly in JSX."]

        [:p "”Native React components” can now be used directly in
        Reagent’s hiccup forms, using this syntax: "[:code "[:>
        nativeComp {:key \"value\"}]"]". This might sometimes be more
        convenient than using "[:code "adapt-react-class"]". "]

        [:p "Reagent should now also be a bit easier to use in
        node.js. If global React is not
        defined (i.e "[:code "React"]", "[:code "ReactDOM"]"
        and "[:code "ReactDOMServer"]"), Reagent tries to
        use "[:code "require"]" instead, to get react, react-dom and
        react-dom/server from npm."]]

       [:section.demo-text
        [:h2 "Better cursor"]

        [:p "Cursors are now cached, which should make them a bit
        easier to use. Previously, every instance
        of "[:code "cursor"]" had its own state.
        Now "[:code "cursor"]"s called with the same arguments share
        data, which means that components like this now make sense: "]

        [demo-component {:comp cursor-example
                         :src (s/src-of [:cursor-example])}]

        [:p "Previously cursors were really only useful (in the sense
        that unnecessary re-renderings were avoided) when passed as
        arguments to child components."]]

       [:section.demo-text
        [:h2 "Tapping into the rendering loop"]

        [:p "The "[:code "next-tick"]" function now has a more
        predictable timing. The function passed
        to "[:code "next-tick"]" is now invoked immediately before the
        next rendering (which is in turn triggered
        using "[:code "requestAnimationFrame"]")."]

        [:p [:code "after-update"]" works just
        like "[:code "next-tick"]", except that the function given is
        invoked immediately "[:b "after"]" the next rendering."]]



       ])]])

(tools/register-page url [#'main] title)
