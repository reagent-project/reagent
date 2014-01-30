(ns reagentdemo.news.async
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.debug :refer-macros [dbg println]]
            [reagentdemo.syntax :refer-macros [get-source]]
            [reagentdemo.page :refer [title link page-map]]
            [reagentdemo.common :as common :refer [demo-component]]))

(def funmap (-> "reagentdemo/news/async.cljs" get-source common/fun-map))
(def src-for (partial common/src-for funmap))

(defn timing-wrapper [{f :component-fn}]
  (let [start-time (atom nil)
        render-time (atom nil)
        now #(.now js/Date)
        start (fn [] (reset! start-time (now)) nil)
        stop #(reset! render-time (- (now) @start-time))
        timed-f (with-meta f
                  {:get-initial-state start
                   :component-will-update start
                   :component-did-mount stop
                   :component-did-update stop})]
    (fn [props children]
      [:div
       [:p [:em "render time: " @render-time "ms"]]
       (into [timed-f props] children)])))

(def base-color (atom {:red 130 :green 160 :blue 120}))
(def ncolors (atom 20))
(def random-colors (atom nil))

(defn to-rgb [{:keys [red green blue]}]
  (let [hex (fn [x]
              (str (if (< x 16) "0")
                   (-> x js/Math.round (.toString 16))))]
    (str "#" (hex red) (hex green) (hex blue))))

(defn tweak-color [{:keys [red green blue]}]
  (let [rnd #(-> (js/Math.random) (* 256))
        tweak #(-> % (+ (rnd)) (/ 2) js/Math.floor)]
    {:red (tweak red) :green (tweak green) :blue (tweak blue)}))

(defn reset-random-colors []
  (reset! random-colors
          (repeatedly #(-> @base-color tweak-color to-rgb))))

(defn color-choose [{color-part :color-part}]
  [:div (name color-part) " " (color-part @base-color)
   [:input {:type "range" :min 0 :max 255
            :style {:width "100%"}
            :value (color-part @base-color)
            :on-change
            (fn [e]
              (swap! base-color assoc
                     color-part (-> e .-target .-value int))
              (reset-random-colors))}]])

(defn ncolors-choose []
  [:div
   "number of colors " @ncolors
   [:input {:type "range" :min 0 :max 500
            :style {:width "100%"}
            :value @ncolors
            :on-change #(reset! ncolors (-> % .-target .-value))}]])

(defn color-plate [{color :color}]
  [:div.color-plate
   {:style {:background-color color}}])

(defn palette []
  (let [color @base-color
        n @ncolors]
    [:div
     [:div
      [:p "base color: "]
      [color-plate {:color (to-rgb color)}]]
     [:div.color-samples
      [:p n " random matching colors:"]
      (map-indexed (fn [k v]
                     [color-plate {:key k :color v}])
                   (take n @random-colors))]]))

(defn color-demo []
  (reset-random-colors)
  (fn []
    [:div
     [:h2 "Matching colors"]
     [color-choose {:color-part :red}]
     [color-choose {:color-part :green}]
     [color-choose {:color-part :blue}]
     [ncolors-choose]
     [timing-wrapper {:component-fn palette}]]))

(defn main []
  [:div.reagent-demo
   [title "Reagent: Faster by waiting"]
   [:h1 [link {:href main} "Faster by waiting"]]

   [demo-component {:comp color-demo
                    :src (src-for
                          [:ns :timing-wrapper :base-color
                           :ncolors :random-colors :to-rgb
                           :tweak-color :reset-random-colors :color-choose
                           :ncolors-choose :palette :color-demo])}]])

(swap! page-map assoc
       "news/reagent-is-async.html" main)
