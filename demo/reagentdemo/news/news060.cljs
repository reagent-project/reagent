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
                            {1 {:name "John Smith"
                                :title "Manager"}
                             2 {:name "Maggie Johnson"
                                :title "Senior Executive Manager"}}}))

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

(defn dispatch [e]
  ;; (js/console.log "Handling event" (str e))
  (r/rswap! app-state event-handler e))

(defn name-edit [id]
  (let [p @(r/track person id)]
    [:div
     [:input {:value (:name p)
              :on-change #(dispatch [:set-name id (.-target.value %)])}]]))

(defn edit-fields []
  (let [ids @(r/track person-keys)]
    [:div
     (for [i ids]
       ^{:key i} [name-edit i])
     [:input {:type 'button
              :value "Add person"
              :on-click #(dispatch [:add-person])}]]))


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

       [:h2 "Handling destruction"]

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

       [:p [:code "with-let"] " can also be combined with " [:code "track"] ". For
       example, the component above could also be written as: "]

       [demo-component {:comp tracked-pos
                        :src (s/src-of [:mouse-pos
                                        :tracked-pos])}]

       [:p "The " [:code "finally"] " clause will run
       when " [:code "mouse-pos"] " is no longer tracked anywhere, i.e
       in this case when " [:code "tracked-pos"] "is unmounted."]


       [:section.demo-text
        [:h2 "Event handling with rswap!"]

        [:p [:code "rswap!"] " is another new function in 0.6.0. It
        works like standard "[:code "swap!"]" except that it"]

        [:ul
         [:li "always returns nil"]
         [:li "allows recursive applications of "[:code "rswap!"]" on the same
         atom."]]

        [:p "Here’s an example that uses "[:code "rswap!"]" to edit
        the data introduced in the section about track above:"]

        [demo-component {:comp edit-fields
                         :src (s/src-of [:event-handler
                                         :dispatch
                                         :name-edit
                                         :edit-fields])}]

        [:p "All events are passed through the "[:code "dispatch"]"
        function, consisting of a trivial application
        of "[:code "rswap!"]" and some optional logging. This is the
        only place where application state actually changes – the rest
        is pure functions all the way."]

        [:p "The actual event handling is done
        in "[:code "event-handler"]", which takes state and event as
        parameters, and returns a new state (events are represented by
        vectors, with an event name in the first position)."]

        [:p "All the UI components have to do is then just to return
        some markup, and set up routing of events through the dispatch
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
        cause severe headaches if an event handler called by dispatch
        itself dispatches a new event (that would result in lost
        events, and much confusion)."]]

       ])]])

(tools/register-page url [#'main] title)
