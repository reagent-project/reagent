
(ns reagentdemo.intro
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.debug :refer-macros [dbg println]]
            [clojure.string :as string]
            [reagentdemo.syntax :refer-macros [get-source]]
            [reagentdemo.page :refer [link title]]
            [reagentdemo.common :as common :refer [demo-component]]))

(defn simple-component []
  [:div
   [:p "I am a component!"]
   [:p.someclass 
    "I have " [:strong "bold"]
    [:span {:style {:color "red"}} " and red "] "text."]])

(defn simple-parent []
  [:div
   [:p "I include simple-component."]
   [simple-component]])

(defn lister [props]
  [:ul
   (for [item (:items props)]
     [:li {:key item} "Item " item])])

(defn lister-user []
  [:div
   "Here is a list:"
   [lister {:items (range 3)}]])

(def click-count (atom 0))

(defn counting-component []
  [:div
   "The atom " [:code "click-count"] " has value: "
   @click-count ". "
   [:input {:type "button" :value "Click me!"
            :on-click #(swap! click-count inc)}]])

(defn atom-input [{:keys [value]}]
  [:input {:type "text"
           :value @value
           :on-change #(reset! value (-> % .-target .-value))}])

(defn shared-state []
  (let [val (atom "foo")]
    (fn []
      [:div
       [:p "The value is now: " @val]
       [:p "Change it here: "
        [atom-input {:value val}]]])))

(defn timer-component []
  (let [seconds-elapsed (atom 0)]
    (fn []
      (js/setTimeout #(swap! seconds-elapsed inc) 1000)
      [:div
       "Seconds Elapsed: " @seconds-elapsed])))

(defn render-simple []
  (reagent/render-component [simple-component]
                            (.-body js/document)))

(defn calc-bmi [params to-calc]
  (let [{:keys [height weight bmi]} params
        h (/ height 100)]
    (case to-calc
      :bmi (assoc params :bmi (/ weight (* h h)))
      :weight (assoc params :weight (* bmi h h)))))

(def bmi-data (atom (calc-bmi {:height 180 :weight 80} :bmi)))

(defn set-bmi [key val]
  (swap! bmi-data #(calc-bmi (assoc % key val)
                             (case key :bmi :weight :bmi))))

(defn slider [{:keys [value min max param]}]
  [:input {:type "range" :value value :min min :max max
           :style {:width "100%"}
           :on-change #(set-bmi param (-> % .-target .-value))}])

(defn bmi-component []
  (let [{:keys [weight height bmi]} @bmi-data
        [color diagnose] (cond
                          (< bmi 18.5) ["orange" "underweight"]
                          (< bmi 25) ["inherit" "normal"]
                          (< bmi 30) ["orange" "overweight"]
                          :else ["red" "obese"])]
    [:div
     [:h3 "BMI calculator"]
     [:div
      "Height: " (int height) "cm"
      [slider {:value height :min 100 :max 220 :param :height}]]
     [:div
      "Weight: " (int weight) "kg"
      [slider {:value weight :min 30 :max 150 :param :weight}]]
     [:div
      "BMI: " (int bmi) " "
      [:span {:style {:color color}} diagnose]
      [slider {:value bmi :min 10 :max 50 :param :bmi}]]]))

(def funmap (-> "reagentdemo/intro.cljs" get-source common/fun-map))
(def src-for (partial common/src-for funmap))

(defn intro []
  (let [github {:href "https://github.com/holmsand/reagent"}
        clojurescript {:href "https://github.com/clojure/clojurescript"}
        react {:href "http://facebook.github.io/react/"}
        hiccup {:href "https://github.com/weavejester/hiccup"}]
    [:div.demo-text

     [:h2 "Introduction to Reagent"]

     [:p [:a github "Reagent"] " provides a minimalistic interface
     between " [:a clojurescript "ClojureScript"] " and " [:a
     react "React"] ". It allows you to define efficient React
     components using nothing but plain ClojureScript functions and
     data, that describe your UI using a " [:a hiccup "Hiccup"] "-like
     syntax."]

     [:p "The goal of Reagent is to make it possible to define
     arbitrarily complex UIs using just a couple of basic concepts,
     and to be fast enough by default that you rarely have to care
     about performance."]

     [:p "A very basic Reagent component may look something like this: "]
     [demo-component {:comp simple-component
                      :src (src-for [:simple-component])}]

     [:p "You can build new components using other components as
     building blocks. Like this:"]
     [demo-component {:comp simple-parent
                      :src (src-for [:simple-parent])}]

     [:p "Data is passed to child components using plain old Clojure
     maps. For example, here is a component that shows items in a "
     [:code "seq"] ":" ]

     [demo-component {:comp lister-user
                      :src (src-for [:lister :lister-user])}]

     [:p [:strong "Note: "]
     "The " [:code "{:key item}"] " part of the " [:code ":li"] "
     isn’t really necessary in this simple example, but passing a
     unique key for every item in a dynamically generated list of
     components is good practice, and helps React to improve
     performance for large lists."]]))

(defn managing-state []
  [:div.demo-text
   [:h2 "Managing state in Reagent"]

   [:p "The easiest way to manage state in Reagent is to use Reagent’s
   own version of " [:code "atom"] ". It works exactly like the one in
   clojure.core, except that it keeps track of every time it is
   deref’ed. Any component that uses an " [:code "atom"]" is automagically
   re-rendered when its value changes."]

   [:p "Let’s demonstrate that with a simple example:"]
   [demo-component {:comp counting-component
                    :src (src-for [:ns :click-count :counting-component])}]

   [:p "Sometimes you may want to maintain state locally in a
   component. That is easy to do with an " [:code "atom"] " as well."]

   [:p "Here is an example of that, where we call "
    [:code "setTimeout"] " every time the component is rendered to
   update a counter:"]
   
   [demo-component {:comp timer-component
                    :src (src-for [:timer-component])}]
   
   [:p "The previous example also uses another feature of Reagent: a component
   function can return another function, that is used to do the actual
   rendering. This allows you to perform some setup of newly
   created components, without resorting to React’s lifecycle
   events."]

   [:p "By simply passing atoms around you can share state management
   between components, like this:"]
   [demo-component {:comp shared-state
                    :src (src-for [:ns :atom-input :shared-state])}]

   [:p [:strong "Note: "] "Component functions (including the ones
    returned by other component functions) are called with three
    arguments: "]
   [:ul
    [:li [:code "props"] ": a map passed from a parent" ]
    [:li [:code "children"] ": a vector of the children passed to the component"]
    [:li [:code "this"] ": the actual React component"]]])

(defn essential-api []
  [:div.demo-text
   [:h2 "Essential API"]

   [:p "Reagent supports most of React’s API, but there is really only
   one entry-point that is necessary for most applications: "
    [:code "reagent.core/render-component"] "."]

   [:p "It takes two arguments: a component, and a DOM node. For
   example, splashing the very first example all over the page would
   look like this:"]

   [demo-component {:src (src-for [:ns :simple-component :render-simple])}]])

(defn performance []
  [:div.demo-text
   [:h2 "Performance"]

   [:p "React itself is very fast, and so is Reagent. In fact, Reagent
   will be even faster than plain React a lot of the time, thanks to
   optimizations made possible by ClojureScript."]

   [:p "Mounted components are only re-rendered when their parameters
   have changed. The change could come from a deref’ed "
   [:code "atom"] ", the arguments passed to the component (i.e the
   ”props” map and children) or component state."]

   [:p "All of these are checked for changes with a simple "
   [:code "identical?"] " which is basically only a pointer
   comparison, so the overhead is very low (even if the components of
   the props map are compared separately, and "
   [:code ":style"] " attributes are handled specially). Even the
   built-in React components are handled the same way."]

   [:p "All this means that you (hopefully) simply won’t have to care
   about performance most of the time. Just define your UI however you like
   – it will be fast enough."]

   [:p "There are a couple of situations that you might have to care
   about, though. If you give Reagent big " [:code "seq"] "s of
   components to render, you might have to supply all of them with a
   unique " [:code ":key"] " attribute to speed up rendering. Also note
   that anonymous functions are not, in general, equal to each other
   even if they represent the same code and closure."]

   [:p "But again, in general you should just trust that React and
   Reagent will be fast enough. This very page is composed of a single
   Reagent component with thousands of child components (every single
   parenthesis etc in the code examples is a separate component), and
   yet the page can be updated many times every second without taxing
   the browser the slightest."]

   [:p "Incidentally, this page also uses another React trick: the
   entire page is pre-rendered using Node, and "
   [:code "reagent/render-component-to-string"] ". When it is loaded
   into the browser, React automatically attaches event-handlers to
   the already present DOM tree."]])

(defn bmi-demo []
  [:div.demo-text
   [:h2 "Putting it all together"]
   
   [:p "Here is a slightly less contrived example: a simple BMI
   calculator."]

   [:p "Data is kept in a single " [:code "reagent.core/atom"] ": a map
   with height, weight and BMI as keys."]

   [demo-component {:comp bmi-component
                    :src (src-for [:ns :calc-bmi :bmi-data :set-bmi :slider
                                   :bmi-component])}]])

(defn complete-simple-demo []
  [:div.demo-text
   [:h2 "Complete demo"]

   [:p "Reagent comes with a couple of complete examples, with
   Leiningen project files and everything. Here’s one of them in
   action:"]
   
   [demo-component {:comp simpleexample/simple-example
                    :complete true
                    :src (-> "simpleexample.cljs"
                             get-source
                             common/syntaxify)}]])

(defn todomvc-demo []
  [:div.demo-text
   [:h2 "Todomvc"]

   [:p "The obligatory todo list looks roughly like this in
   Reagent (cheating a little bit by skipping routing and
   persistence):"]
   
   [demo-component {:comp todomvc/todo-app
                    :complete true
                    :src (-> "todomvc.cljs"
                             get-source
                             common/syntaxify)}]])

(defn main []
  (let [show-all (atom false)
        head "Reagent: Minimalistic React for ClojureScript"]
    (js/setTimeout #(reset! show-all true) 500)
    (fn []
      [:div.reagent-demo
       [title head]
       [:h1 head]
       [intro]
       [managing-state]
       [essential-api]
       [bmi-demo]
       [performance]
       ;; Show heavy examples on load, to make html file smaller
       (when @show-all [complete-simple-demo])
       (when @show-all [todomvc-demo])])))
