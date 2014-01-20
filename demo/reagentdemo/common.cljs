(ns reagentdemo.common
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.debug :refer-macros [dbg println]]
            [clojure.string :as string]
            [reagentdemo.page :as rpage]
            [reagentdemo.syntax :as syntax]))


(def page rpage/page)

(def title-atom (atom "Reagent: Minimalistic React for ClojureScript"))

(def page-map (atom nil))
(def reverse-page-map (atom nil))

(defn set-page-map [m]
  (reset! page-map m)
  (reset! reverse-page-map (into {} (for [[k [p c]] m]
                                      [p [k c]]))))

(defn prefix [href]
  (let [depth (-> #"/" (re-seq @page) count)
        pref (->> "../" (repeat depth) (apply str))]
    (str pref href)))

(defn link [props children]
  (let [pm @page-map
        href (-> props :href pm first)]
    (assert href)
    (apply vector :a (assoc props
                       :href (prefix href)
                       :on-click (if rpage/history
                                   (fn [e]
                                     (.preventDefault e)
                                     (reset! page href))
                                   identity))
           children)))

(defn title [props children]
  (let [name (first children)]
    (if reagent/is-client
      (let [title (aget (.getElementsByTagName js/document "title") 0)]
        (set! (.-innerHTML title) (dbg name))))
    (reset! title-atom name)
    [:div]))

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
  (string/join "\n" (-> srcmap
                        (select-keys names)
                        vals)))

(defn fun-map [src]
  (-> src src-parts src-defs (assoc :ns nssrc)))

(defn src-for [funmap defs]
  [:pre (-> funmap (src-for-names defs) syntaxify)])

(defn demo-component [{:keys [comp src complete]}]
  (let [showing (atom true)]
    (fn []
      [:div
       (when comp
         [:div.demo-example
          [:a.demo-example-hide {:on-click (fn [e]
                                             (.preventDefault e)
                                             (swap! showing not))}
           (if @showing "hide" "show")]
          [:h3.demo-heading "Example "]
          (when @showing
            (if-not complete
              [:div.simple-demo [comp]]
              [comp]))])
       (when @showing
         [:div.demo-source
          [:h3.demo-heading "Source"]
          src])])))
