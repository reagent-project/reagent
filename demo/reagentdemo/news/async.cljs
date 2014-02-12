(ns reagentdemo.news.async
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.debug :refer-macros [dbg println]]
            [reagentdemo.syntax :refer-macros [get-source]]
            [reagentdemo.page :refer [title link page-map]]
            [reagentdemo.common :as common :refer [demo-component]]))

(def funmap (-> "reagentdemo/news/async.cljs" get-source common/fun-map))
(def src-for (partial common/src-for funmap))

(defn timing-wrapper [f]
  (let [start-time (atom nil)
        render-time (atom nil)
        now #(.now js/Date)
        start #(reset! start-time (now))
        stop #(reset! render-time (- (now) @start-time))
        timed-f (with-meta f
                  {:component-will-mount start
                   :component-will-update start
                   :component-did-mount stop
                   :component-did-update stop})]
    (fn []
      [:div
       [:p [:em "render time: " @render-time "ms"]]
       [timed-f]])))

(def base-color (atom {:red 130 :green 160 :blue 120}))
(def ncolors (atom 20))
(def random-colors (atom nil))

(defn to-rgb [{:keys [red green blue]}]
  (let [hex #(str (if (< % 16) "0")
                  (-> % js/Math.round (.toString 16)))]
    (str "#" (hex red) (hex green) (hex blue))))

(defn tweak-color [{:keys [red green blue]}]
  (let [rnd #(-> (js/Math.random) (* 256))
        tweak #(-> % (+ (rnd)) (/ 2) js/Math.floor)]
    {:red (tweak red) :green (tweak green) :blue (tweak blue)}))

(defn reset-random-colors [color]
  (reset! random-colors
          (repeatedly #(-> color tweak-color to-rgb))))

(defn color-choose [color-part]
  [:div.color-slider
   (name color-part) " " (color-part @base-color)
   [:input {:type "range" :min 0 :max 255
            :value (color-part @base-color)
            :on-change (fn [e]
                         (swap! base-color assoc
                                color-part (-> e .-target .-value int))
                         (reset-random-colors @base-color))}]])

(defn ncolors-choose []
  [:div.color-slider
   "number of color divs " @ncolors
   [:input {:type "range" :min 0 :max 500
            :value @ncolors
            :on-change #(reset! ncolors (-> % .-target .-value))}]])

(defn color-plate [color]
  [:div.color-plate
   {:style {:background-color color}}])

(defn palette []
  (let [color @base-color
        n @ncolors]
    [:div
     [:div
      [:p "base color: "]
      [color-plate (to-rgb color)]]
     [:div.color-samples
      [:p n " random matching colors:"]
      (map-indexed (fn [k v]
                     ^{:key k} [color-plate v])
                   (take n @random-colors))]]))

(defn color-demo []
  (reset-random-colors @base-color)
  (fn []
    [:div
     [:h2 "Matching colors"]
     [color-choose :red]
     [color-choose :green]
     [color-choose :blue]
     [ncolors-choose]
     [timing-wrapper palette]]))

(defn main [{:keys [summary]}]
  (let [om-article {:href "http://swannodette.github.io/2013/12/17/the-future-of-javascript-mvcs/"}]
    [:div.reagent-demo
     [title "Reagent: Faster by waiting"]
     [:h1 [link {:href main} "Faster by waiting"]]
     [:div.demo-text
      [:h2 "Reagent gets async rendering"]

      [:p "Reagent already separates state from components. Now they
      are also separated in time."]
     
      [:p "From version 0.3.0, changes in application state (as
      represented by Reagent’s " [:code "atom"] "s) are no longer
      rendered immediately to the DOM. Instead, Reagent waits until
      the browser is ready to repaint the window, and then all the
      changes are rendered in one single go."]

      (if summary
        [link {:href main
               :class 'news-read-more} "Read more"]
        [:div.demo-text

         [:p "This is good for all sorts of reasons:"]
         [:ul
      
          [:li "Reagent doesn't have to spend time doing renderings
          that no one would ever see (because changes to application
          state happened faster than the browser could repaint)."]

          [:li "If two or more atoms are changed simultaneously, this
          now leads to only one re-rendering, and not two."]

          [:li "The new code does proper batching of renderings even
          when changes to atoms are done outside of event
          handlers (which is great for e.g core.async users)."]

          [:li "Repaints can be synced by the browser with for example
          CSS transitions, since Reagent uses requestAnimationFrame
          to do the batching. That makes for example animations
          smoother."]]

         [:p "In short, Reagent renders less often, but at the right
         times. For a much better description of why async rendering
         is good, see David Nolen’s " [:a om-article "excellent
         explanation here."]]

         [:h2 "The bad news"]

         [:p "Lunches in general tend to be non-free, and this is no
         exception… The downside to async rendering is that you can no
         longer depend on changes to atoms being immediately available
         in the DOM. (Actually, you couldn’t before either, since
         React.js itself does batching inside event handlers.)"]

         [:p "This may make testing a bit more verbose: you now have
         to call " [:code "reagent.core/flush"] " to force Reagent to
         synchronize state with the DOM."]

         [:h2 "An example"]

         [:p "Here is an example to (hopefully) demonstrate the
         virtues of async rendering. It consists of a simple color
         chooser (three sliders to set the red, green and blue
         components of a base color), and shows the base color + a
         bunch of divs in random matching colors. As soon as the base
         color is changed, a new set of random colors is shown."]

         [:p "If you change one of the base color components, the base
         color should change immediately, and smoothly (on my Macbook
         Air, rendering takes around 2ms, with 20 colored divs
         showing)."]

         [:p "But perhaps more interesting is to see what happens when
         the updates can’t be made smoothly (because the browser
         simply cannot re-render the colored divs quickly enough). On
         my machine, this starts to happen if I change the number of
         divs shown to above 150 or so."]

         [:p "As you increase the number of divs, you’ll notice that
         the base color no longer changes quite so smoothly when you
         move the color sliders."]

         [:p "But the crucial point is that the sliders "
          [:strong "still work"] ". Without async rendering, you could
         quickly get into a situation where the browser hangs for a
         while, doing updates corresponding to an old state. "]

         [:p "With async rendering, the only thing that happens is
         that the frame rate goes down."]

         [:p "Btw, I find it quite impressive that React manages to
         change 500 divs (12 full screens worth) in slightly more than
         40ms. And even better: when I change the number of divs
         shown, it only takes around 6ms to re-render the color
         palette (because the individual divs don’t have to be
         re-rendered, divs are just added or removed from the DOM as
         needed)."]

         [demo-component
          {:comp color-demo
           :src (src-for
                 [:ns :timing-wrapper :base-color :ncolors
                  :random-colors :to-rgb :tweak-color
                  :reset-random-colors :color-choose :ncolors-choose
                  :palette :color-demo])}]])]]))

(swap! page-map assoc
       "news/reagent-is-async.html" main)
