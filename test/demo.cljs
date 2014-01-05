(ns demo
  (:require [cloact.core :as cloact :refer [atom]]
            [clojure.string :as string])
  (:require-macros [demoutil :refer [get-source]]
                   [cloact.debug :refer [dbg println]]))

(def demosrc (get-source "demo.cljs"))

(defn src-parts [src]
  (->>
   (string/split src #"\n\(")
   rest
   (map #(str "(" %))))

(defn src-defs [parts]
  (into {} (for [x parts]
             [(keyword (nth (string/split x #"\s+") 1))
              x])))

(def srcmap
  (-> "demo.cljs"
      get-source
      src-parts
      src-defs))

(def nssrc
  "(ns example
  (:require [cloact.core :as cloact :refer [atom]])
")

(defn src-for-names [names]
  (let [defs (merge srcmap {:ns nssrc})]
    (string/join "\n"
                 (map #(% defs) names))))

(def builtins ["def" "defn" "ns" "atom" "let" "if" "when"
               "cond" "merge" "assoc" "swap!" "reset!" "for"
               "range" "nil\\?" "int" "or" "->" "%"])

(defn syntaxify [src]
  ;; quick and (very) dirty syntax coloring
  (let [sep "\\][(){} \\t\\n"
        str-p "\"[^\"]*\""
        keyw-p (str ":[^" sep "]+")
        res-p (string/join "|" (map #(str "\\b" % "(?=[" sep "])") builtins))
        any-p (str "[^" sep "]+|.|\\n")
        patt (re-pattern (str "("
                              (string/join ")|(" [str-p keyw-p res-p any-p])
                              ")"))]
    (apply vector :pre
           (for [[s str keyw res] (re-seq patt src)]
             (cond
              str [:span {:style {:color "green"}} str]
              keyw [:span {:style {:color "blue"}} keyw]
              res [:b res]
              :else s)))))

(defn src-for [defs]
  [:pre (syntaxify (src-for-names defs))])

(defn demo-component [{:keys [comp defs]}]
  [:div
   (when comp
     [:div.demo-example
      [:h3.demo-heading "Example"]
      [comp]])
   [:div.demo-source
    [:h3.demo-heading "Source"]
    (src-for defs)]])

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
  (cloact/render-component [simple-component]
                           (.-body js/document)))

(defn calc-bmi [{:keys [height weight bmi] :as params}]
  (let [h (/ height 100)]
    (if (nil? bmi)
      (assoc params :bmi (/ weight (* h h)))
      (assoc params :weight (* bmi h h)))))

(def bmi-data (atom (calc-bmi {:height 180 :weight 80})))

(defn set-bmi [key val clear]
  (swap! bmi-data #(calc-bmi (merge % {key val, clear nil}))))

(defn slider [{:keys [value min max param clear]}]
  [:div
   [:input {:type "range" :min min :max max :value value
            :style {:width "100%"}
            :on-change #(set-bmi param (-> % .-target .-value)
                                 (or clear :bmi))}]])

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
      [slider {:value bmi :min 10 :max 50 :param :bmi
               :clear :weight}]]]))

(defn intro []
  [:div
   [:h2 "Introduction to Cloact"]

   [:p [:a {:href "https://github.com/holmsand/cloact"} "Cloact"]
    " provides a minimalistic interface between "
    [:a {:href "https://github.com/clojure/clojurescript"} "ClojureScript"]
    " and " [:a {:href "http://facebook.github.io/react/"} "React"]
    ". It allows you to define React components using nothing but
    plain ClojureScript functions, that describe your UI using a
    Hiccup-like syntax."]

   [:p "A very basic component may look something like this: "]
   [demo-component {:comp simple-component
                    :defs [:simple-component]}]

   [:p "You can build new components using other components as
   building blocks. That looks like this:"]
   [demo-component {:comp simple-parent
                    :defs [:simple-parent]}]

   [:p "Data is passed to child components using a plain old Clojure
   maps. For example, here is a component that shows items in a "
    [:code "seq"] "." ]

   [demo-component {:comp lister-user
                    :defs [:lister :lister-user]}]

   [:p [:strong "Note: "]
    "The " [:code "{:key item}"] " part of the " [:code ":li"] " isn't
    really necessary in this simple example, but passing a unique key
    for every item in a dynamically generated list of components is
    good practice, and helps React to improve performance a lot for
    large lists."]])

(defn managing-state []
  [:div
   [:h2 "Managing state in Cloact"]

   [:p "The easiest way to manage state in Cloact is to use Cloact's
   own version of " [:code "atom"] ". It works exactly like the one in
   clojure.core, except that it keeps track of every time it is
   deref'ed. Any component that uses an " [:code "atom"]" is automagically
   re-rendered when its value changes."]

   [:p "Let's demonstrate that with a simple example:"]
   [demo-component {:comp counting-component
                    :defs [:ns :click-count :counting-component]}]

   [:p "Sometimes you may want to maintain state locally in a
   component. That is easy to do with an " [:code "atom"] " as well."]

   [:p "Here is an example of that, where we call setTimeout every
   time the component is rendered to update a simple clock:"]
   
   [demo-component {:comp timer-component
                    :defs [:timer-component]}]
   
   [:p "The previous example also uses another feature of Cloact: a component
   function can return another function, that is used to do the actual
   rendering. It is called with the same arguments as any other
   component function. This allows you to perform some setup of newly
   created components, without resorting to React's lifecycle
   events."]

   [:p "By simply passing atoms around you can share state management
   between components, like this:"]
   [demo-component {:comp shared-state
                    :defs [:ns :atom-input :shared-state]}]])

(defn essential-api []
  [:div
   [:h2 "Essential API"]

   [:p "Cloact supports most of React's API, but there is really only
   one entry-point that is necessary for most applications: "
    [:code "cloact.core/render-component"] "."]

   [:p "It takes too arguments: a component, and a DOM node. For
   example, splashing the very first example all over the page would
   look like this:"]

   [demo-component {:defs [:ns :simple-component :render-simple]}]])

(defn bmi-demo []
  [:div
   [:h2 "Putting it all together"]
   
   [:p "Here is a slightly less contrived example: a simple BMI
   calculator."]

   [:p "Data is kept in a single " [:code "cloact.core/atom"] ": a map
   with height, weight and BMI as keys."]

   [demo-component {:comp bmi-component
                    :defs [:ns :calc-bmi :bmi-data :set-bmi :slider
                           :bmi-component]}]])

(defn demo []
  [:div.cloact-demo
   [:h1 "This will become a demo"]
   [intro]
   [managing-state]
   [essential-api]
   [bmi-demo]
   [:p "WIP"]])

(defn ^:export mountdemo []
  (cloact/render-component [demo] (.-body js/document)))

(defn ^:export genpage []
  (cloact/render-component-to-string [demo]))
