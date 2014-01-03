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
               "cond" "merge"])

(defn syntaxify [src]
  (let [str-p "\"[^\"]*\""
        keyw-p ":[^\\][(){} \\t\\n]+"
        res-p (string/join "\\b|" builtins)
        any-p ".|\\n"
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

(defn src-for [{:keys [defs]}]
  [:pre (syntaxify (src-for-names defs))])

(defn demo-component [props]
  [:div.example
   [:h3 "Example"]
   [(:comp props)]
   [:h3 "Source"]
   [src-for props]])

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
     [:li "Item " item])])

(defn lister-user []
  [:div
   "Here is a list:"
   [lister {:items (range 3)}]])

(def click-count (atom 0))

(defn counting-component []
  [:div
   "The atom " [:code "click-count"] " has value: " @click-count ". "
   [:input {:type "button"
            :value "Click me!"
            :on-click #(swap! click-count inc)}]])

(defn local-state []
  (let [val (atom "foo")]
    (fn []
      [:div
       [:p "The value of " [:code "val"] " is now: " @val]
       [:p "Change it: "
        [:input {:type "text"
                 :value @val
                 :on-change #(reset! val (-> % .-target .-value))}]]])))

(defn calc-bmi [{:keys [height weight bmi] :as params}]
  (let [h (/ height 100)]
    (if (nil? bmi)
      (assoc params :bmi (/ weight (* h h)))
      (assoc params :weight (* bmi h h)))))

(def bmi-data (atom (calc-bmi {:height 180 :weight 80})))

(defn set-bmi [key val clear]
  (swap! bmi-data #(calc-bmi (merge % {key val, clear nil}))))

(defn slider [{:keys [value key clear min max]}]
  [:div
   [:input {:type "range" :min min :max max :value value
            :style {:width "100%"}
            :on-change #(set-bmi key (-> % .-target .-value)
                                 (or clear :bmi))}]])

(defn bmi-component []
  (let [{:keys [weight height bmi]} @bmi-data
        [color diagnose] (cond
                          (< bmi 18.5) ["orange" "underweight"]
                          (< bmi 25) ["inherit" "normal"]
                          (< bmi 30) ["orange" "overweight"]
                          :else ["red" "obese"])]
    [:div
     [:div
      "Height: " (int height) "cm"
      [slider {:value height :min 100 :max 220 :key :height}]]
     [:div
      "Weight: " (int weight) "kg"
      [slider {:value weight :min 50 :max 200 :key :weight}]]
     [:div
      "BMI: " (int bmi) " "
      [:span {:style {:color color}} diagnose]
      [slider {:value bmi :min 10 :max 50 :key :bmi :clear :weight}]]]))

(defn intro []
  [:div
   [:h2 "Introduction to Cloact"]

   [:p "Cloact provides a minimalistic interface between ClojureScript
and React. It allows you to define React components using nothing but
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
                    :defs [:lister :lister-user]}]])

(defn managing-state []
  [:div
   [:h2 "Managing state in Cloact"]

   [:p "The easiest way to manage state in Cloact is to use Cloact's
   own version of " [:code "atom"] ". It works exactly like the one in
   clojure.core, except that it keeps track of every time it is
   deref'ed. Any component that uses the atom is automagically
   re-rendered."]

   [:p "Let's demonstrate that with a simple example:"]
   [demo-component {:comp counting-component
                    :defs [:ns :click-count :counting-component]}]

   [:p "Sometimes you may want to maintain state locally in a
   component. That is very easy to do with an " [:code "atom"] " as well."]

   [:p "Here is an example of that:"]
   [demo-component {:comp local-state
                    :defs [:ns :local-state]}]

   [:p "This example also uses another feature of Cloact: a component
   function can return another function, that is used to do the actual
   rendering. It is called with the same arguments as any other
   component function. This allows you to perform some setup of newly
   created components, without resorting to React's lifecycle
   events."]])


(defn bmi-demo []
  [:div
   [:h2 "Simple BMI calculator"]
   [demo-component {:comp bmi-component
                    :defs [:ns :calc-bmi :bmi-data :set-bmi :slider
                           :bmi-component]}]])

(defn demo []
  [:div
   [:h1 "This will become a demo"]
   [intro]
   [managing-state]
   [bmi-demo]
   [:p "WIP"]])
