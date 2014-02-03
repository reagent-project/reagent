(ns reagentdemo.common
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.debug :refer-macros [dbg println]]
            [clojure.string :as string]
            [reagentdemo.page :as rpage]
            [reagentdemo.syntax :as syntax]))

(def syntaxify (memoize syntax/syntaxify))

(defn src-parts [src]
  (string/split src #"\n(?=[(])"))

(defn src-defs [parts]
  (let [ws #"[^ \t]+"]
    (into {} (for [x parts]
               [(->> x (re-seq ws) second keyword) x]))))

(def nssrc
  "(ns example
  (:require [reagent.core :as reagent :refer [atom]]))
")

(defn src-for-names [srcmap names]
  (string/join "\n" (map srcmap names)))

(defn fun-map [src]
  (-> src src-parts src-defs (assoc :ns nssrc)))

(defn src-for [funmap defs]
  [:pre (-> funmap (src-for-names defs) syntaxify)])

(defn demo-component [{:keys [comp src complete]}]
  (let [showing (atom true)]
    (fn []
      [:div
       (when comp
         [:div.demo-example.clearfix
          [:a.demo-example-hide {:on-click (fn [e]
                                             (.preventDefault e)
                                             (swap! showing not)
                                             false)}
           (if @showing "hide" "show")]
          [:h3.demo-heading "Example "]
          (when @showing
            (if-not complete
              [:div.simple-demo [comp]]
              [comp]))])
       (when @showing
         [:div.demo-source.clearfix
          [:h3.demo-heading "Source"]
          src])])))
