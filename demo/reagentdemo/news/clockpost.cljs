(ns reagentdemo.news.clockpost
  (:require [reagentdemo.syntax :as s]
            [sitetools.core :as tools :refer [link]]
            [reagentdemo.common :as common :refer [demo-component]]
            [reagentdemo.news.binaryclock :as binaryclock]))

(def url "/news/binary-clock.html")
(def title "A binary clock")

(defn fn-src [src]
  [demo-component {:src src :no-heading true}])

(defn main [{:keys [summary]}]
  (let [lexclock {:href "http://www.lexicallyscoped.com/2014/01/23/clojurescript-react-om-binary-clock.html"}
        hopclock {:href "http://pmbauer.github.io/2014/01/27/hoplon-binary-clock/"}
        om {:href "https://github.com/swannodette/om"}
        hoplon {:href "http://hoplon.io"}
        clocksrc {:href "https://github.com/reagent-project/reagent/blob/master/demo/reagentdemo/news/binaryclock.cljs"}]

    [:div.reagent-demo
     [:h1 [link {:href url} title]]
     [:span "2014-02-26"]
     [:div.demo-text

      (when-not summary
        [:div
         [:div.clearfix
          [binaryclock/main]]
         [:div [:strong "Click to toggle 1/100th seconds."]]])

      [:p "Fredrik Dyrkell wrote a very nice " [:a lexclock "binary
      clock"] " using " [:a om "Om"] ". I thought I’d replicate that
      using Reagent for fun (another re-write, using "
       [:a hoplon "Hoplon"] ", can be seen " [:a hopclock "here"] ")."]

      [:p "So, without further ado, here is a binary clock using Reagent."]

      (if summary
        [link {:href url :class 'news-read-mode} "Read more"]
        [:div.demo-text

         [fn-src (s/syntaxed "(ns example
  (:require [reagent.core :as r]))")]

         [:p "We start with the basics: The clock is built out of
         cells, with a light colour if the bit the cell corresponds to
         is set."]

         [fn-src (s/src-of [:cell]
                           "reagentdemo/news/binaryclock.cljs")]

         [:p "Cells are combined into columns of four bits, with a
         decimal digit at the bottom."]

         [fn-src (s/src-of [:column]
                           "reagentdemo/news/binaryclock.cljs")]

         [:p "Columns are in turn combined into pairs:"]

         [fn-src (s/src-of [:column-pair]
                           "reagentdemo/news/binaryclock.cljs")]

         [:p "We'll also need the legend on the left side:"]

         [fn-src (s/src-of [:legend]
                           "reagentdemo/news/binaryclock.cljs")]

         [:p "We combine these element into a component that shows the
         legend, hours, minutes and seconds; and optionally 1/100
         seconds. It also responds to clicks."]

         [fn-src (s/src-of [:clock]
                           "reagentdemo/news/binaryclock.cljs")]

         [:p "We also need to keep track of the time, and of the
         detail shown, in a Reagent atom. And a function to update the
         time."]

         [fn-src (s/src-of [:clock-state :update-time]
                           "reagentdemo/news/binaryclock.cljs")]

         [:p "And finally we use the " [:code "clock"] " component.
         The current time is scheduled to be updated, after a suitable
         delay, every time the main component is rendered ("
          [:code "reagent.core/next-tick"] " is just a front for "
          [:code "requestAnimationFrame"] "):"]

         [fn-src (s/src-of [:main]
                           "reagentdemo/news/binaryclock.cljs")]

         [:p "The entire source is also available "
          [:a clocksrc "here"] "."]

         [:h2 "How it all works"]

         [:p "Reading through the source, it may look like the entire
         clock component is recreated from scratch whenever the time
         changes. "]

         [:p "That is an illusion: Reagent and React together
         makes sure that only the parts of the DOM that actually need
         to change are updated. For example, the "
          [:code "column-pair"] " function corresponding to hours only
         runs once every hour."]

         [:p "And that’s what makes Reagent and React fast. Try
         clicking on the clock to toggle the display of 1/100th
         seconds. Most browsers should have no trouble at all keeping
         up (even if they won’t actually show every 1/100th second:
         they are typically limited to roughly 60 fps)."]

         [:p "But it is a very handy illusion. Almost the entire UI is
         made up of pure functions, transforming immutable data into
         other immutable data structures. That makes them easy to
         reason about, and trivial to test. You don’t have to care
         about ”model objects”, or about how to update the DOM
         efficiently. "]

         [:p "Just pass arguments to component functions, return a UI
         description that corresponds to those arguments, and leave it
         to React to actually display that UI."]])]]))

(tools/register-page url [#'main] title)
