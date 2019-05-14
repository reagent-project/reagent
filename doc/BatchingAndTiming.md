# Batching and Timing: How Reagent Renders Changes to Application State

Changes in application state (as represented by Reagent’s `atom`s) are not rendered immediately to the DOM. Instead, Reagent waits until the browser is ready to repaint the window, and then all the changes are rendered in one single go.

This is good for all sorts of reasons:

* Reagent doesn't have to spend time doing renderings that no one would ever see (because changes to application state happened faster than the browser could repaint).
* If two or more atoms are changed simultaneously, this now leads to only one re-rendering, and not two.
* The new code does proper batching of renderings even when changes to atoms are done outside of event handlers (which is great for e.g core.async users).
* Repaints can be synced by the browser with for example CSS transitions, since Reagent uses requestAnimationFrame to do the batching. That makes for example animations smoother.

In short, Reagent renders less often, but at the right times. For a much better description of why async rendering is good, see David Nolen’s [excellent explanation here.](http://swannodette.github.io/2013/12/17/the-future-of-javascript-mvcs)

## The bad news

Lunches in general tend to be non-free, and this is no exception. The downside to async rendering is that you can no longer depend on changes to atoms being immediately available in the DOM. (Actually, you couldn't have truly relied upon it anyway because React.js itself does batching inside event handlers.)

The biggest impact is in testing: be sure to call `reagent.core/flush` to force Reagent to synchronize state with the DOM.

## An example

Here is an example to (hopefully) demonstrate the virtues of async rendering. It consists of a simple color chooser (three sliders to set the red, green and blue components of a base color), and shows the base color + a bunch of divs in random matching colors. As soon as the base color is changed, a new set of random colors is shown.

If you change one of the base color components, the base color should change immediately, and smoothly (on my Macbook Air, rendering takes around 2ms, with 20 colored divs showing).

But perhaps more interesting is to see what happens when the updates can’t be made smoothly (because the browser simply cannot re-render the colored divs quickly enough). On my machine, this starts to happen if I change the number of divs shown to above 150 or so.

As you increase the number of divs, you’ll notice that the base color no longer changes quite so smoothly when you move the color sliders.

But the crucial point is that the sliders **still work**. Without async rendering, you could quickly get into a situation where the browser hangs for a while, doing updates corresponding to an old state.

With async rendering, the only thing that happens is that the frame rate goes down.

Btw, I find it quite impressive that React manages to change 500 divs (12 full screens worth) in slightly more than 40ms. And even better: when I change the number of divs shown, it only takes around 6ms to re-render the color palette (because the individual divs don’t have to be re-rendered, divs are just added or removed from the DOM as needed).

```clojure
(ns example
  (:require [reagent.core :as r]))
(defn timing-wrapper [f]
  (let [start-time (r/atom nil)
        render-time (r/atom nil)
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

(def base-color (r/atom {:red 130 :green 160 :blue 120}))
(def ncolors (r/atom 20))
(def random-colors (r/atom nil))

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
            :on-change #(reset! ncolors (-> % .-target .-value int))}]])

(defn color-plate [color]
  [:div.color-plate
   {:style {:background-color color}}])

(defn palette []
  (let [color @base-color
        n @ncolors]
    [:div
     [:p "base color: "]
     [color-plate (to-rgb color)]
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
```

## Tapping into the rendering loop

The `next-tick` function allows you to tap into the rendering loop. The function passed to `next-tick` is invoked immediately before the next rendering (which is in turn triggered using `requestAnimationFrame`).

The `after-update` is similar: it works just like `next-tick`, except that the function given is invoked immediately after the next rendering.
