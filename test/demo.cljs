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

(defn src-for [{:keys [defs]}]
  [:pre (src-for-names defs)])

(defn demo-component [props]
  [:div.example
   [:h3 "Example"]
   [(:comp props)]
   [:h3 "Source"]
   [src-for props]])

(defn simple-component []
  [:div
   [:h3 "I am a component!"]
   [:p.someclass 
    "I have " [:strong "bold"]
    [:span {:style {:color "red"}} " and red "]
    "text."]])

(defn demo-simple []
  [:div
   [:h2 "This is a simple component"]
   [demo-component {:comp simple-component
                    :defs [:ns :simple-component]}]])

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

(defn bmi-demo []
  [:div
   [:h2 "Simple BMI calculator"]
   [demo-component {:comp bmi-component
                    :defs [:ns :calc-bmi :bmi-data :set-bmi :slider
                           :bmi-component]}]])

(defn demo []
  [:div
   [:h1 "This will become a demo"]
   [demo-simple]
   [bmi-demo]
   [:p "WIP"]])
