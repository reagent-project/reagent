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
   [simple-component]
   [:p "Source:"
    [src-for {:defs [:ns :simple-component]}]]])

(defn demo []
  [:div
   [:h1 "This will become a demo"]
   [demo-simple]
   [:p "WIP"]])
