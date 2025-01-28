<img src="logo/logo-text.png" width="264px" alt="Reagent">

[![Run tests](https://github.com/reagent-project/reagent/actions/workflows/clojure.yml/badge.svg)](https://github.com/reagent-project/reagent/actions/workflows/clojure.yml)
[![Clojars Project](https://img.shields.io/clojars/v/reagent.svg)](https://clojars.org/reagent)
[![codecov](https://codecov.io/gh/reagent-project/reagent/branch/master/graph/badge.svg)](https://codecov.io/gh/reagent-project/reagent)
[![cljdoc badge](https://cljdoc.org/badge/reagent/reagent)](https://cljdoc.org/d/reagent/reagent/CURRENT)
[![project chat](https://img.shields.io/badge/slack-join_chat-brightgreen.svg)](https://clojurians.slack.com/archives/C0620C0C8)

A simple [ClojureScript](http://github.com/clojure/clojurescript) interface to [React](https://reactjs.org/).

Reagent provides a way to write efficient React components using (almost) nothing but plain ClojureScript functions.

  * **[Detailed intro with live examples](http://reagent-project.github.io/)**
  * **[News](http://reagent-project.github.io/news/index.html)**
  * **[Documentation, latest release](https://cljdoc.org/d/reagent/reagent/CURRENT)**
  * **Documentation, next release: [API docs](http://reagent-project.github.io/docs/master/), [Tutorials and FAQ](https://github.com/reagent-project/reagent/tree/master/doc)**
  * **Community discussion and support channels**
    * **[#reagent](https://clojurians.slack.com/messages/reagent/)** channel in [Clojure Slack](http://clojurians.net/)
  * **Commercial video material**
    * [Learn Reagent Free](https://www.jacekschae.com/learn-reagent-free/tycit?coupon=REAGENT), [Learn Reagent Pro](https://www.jacekschae.com/learn-reagent-pro/tycit?coupon=REAGENT) (Affiliate link, $30 discount)
    * [Learn Re-frame Free](https://www.jacekschae.com/learn-re-frame-free/tycit?coupon=REAGENT), [Learn Re-frame Pro](https://www.jacekschae.com/learn-re-frame-pro/tycit?coupon=REAGENT) (Affiliate link, $30 discount)
    * [purelyfunctional.tv ](https://purelyfunctional.tv/guide/reagent/)
    * [Lambda Island Videos](https://lambdaisland.com/collections/react-reagent-re-frame)

### Usage

To create a new Reagent project using [Leiningen](http://leiningen.org/) template simply run:

    lein new reagent myproject

If you wish to only create the assets for ClojureScript without a Clojure backend then do the following instead:

    lein new reagent-frontend myproject

This will setup a new Reagent project with some reasonable defaults, see here for more [details](https://github.com/reagent-project/reagent-template).

To use Reagent in an existing project you add this to your dependencies in `project.clj`:

[![Clojars Project](http://clojars.org/reagent/latest-version.svg)](http://clojars.org/reagent) <br>

And provide React using either npm (when using e.g. [Shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html))

```
npm i react react-dom
```

or by adding [Cljsjs](http://cljsjs.github.io/) React packages to your project:

```
[cljsjs/react "17.0.2-0"]
[cljsjs/react-dom "17.0.2-0"]
```

Note: Reagent is tested against React 17, but should be compatible with other
versions.

## Examples

Reagent uses [Hiccup-like](https://github.com/weavejester/hiccup) markup instead of React's sort-of html. It looks like this:

```clj
(defn some-component []
  [:div
   [:h3 "I am a component!"]
   [:p.someclass
    "I have " [:strong "bold"]
    [:span {:style {:color "red"}} " and red"]
    " text."]])
```

Reagent extends standard Hiccup in one way: it is possible to "squeeze" elements together by using a `>` character.

```clj
[:div
  [:p
    [:b "Nested Element"]]]
```

can be written as:

```clj
[:div>p>b "Nested Element"]
```

> **Since version 0.8:** The `:class` attribute also supports collections of classes, and nil values are removed:
>
> ```clj
> [:div {:class ["a-class" (when active? "active") "b-class"]}]
> ```

You can use one component inside another:

```clj
(defn calling-component []
  [:div "Parent component"
   [some-component]])
```

And pass properties from one component to another:

```clj
(defn child [name]
  [:p "Hi, I am " name])

(defn childcaller []
  [child "Foo Bar"])
```

You mount the component into the DOM like this:

```clj
(defn mountit []
  (rd/render [childcaller]
            (.-body js/document)))
```

assuming we have imported Reagent like this:

```clj
(ns example
  (:require [reagent.core :as r]
            [reagent.dom :as rd]))
```

State is handled using Reagent's version of `atom`, like this:

```clj
(defonce click-count (r/atom 0))

(defn state-ful-with-atom []
  [:div {:on-click #(swap! click-count inc)}
   "I have been clicked " @click-count " times."])
```

Any component that dereferences a `reagent.core/atom` will be automatically re-rendered.

If you want to do some setting up when the component is first created, the component function can return a new function that will be called to do the actual rendering:

```clj
(defn timer-component []
  (let [seconds-elapsed (r/atom 0)]
    (fn []
      (js/setTimeout #(swap! seconds-elapsed inc) 1000)
      [:div
       "Seconds Elapsed: " @seconds-elapsed])))
```

This way you can avoid using React's lifecycle callbacks like `getInitialState` and `componentWillMount` most of the time.

But you can still use them if you want to, either using `reagent.core/create-class` or by attaching meta-data to a component function:

```clj
(defonce my-html (r/atom ""))

(defn plain-component []
  [:p "My html is " @my-html])

(def component-with-callback
  (with-meta plain-component
    {:component-did-mount
     (fn [this]
       (reset! my-html (.-innerHTML (rd/dom-node this))))}))
```

See the examples directory for more examples.


## Performance

React is pretty darn fast, and so is Reagent. It should even be faster than plain old javascript React a lot of the time, since ClojureScript allows us to skip a lot of unnecessary rendering (through judicious use of React's `shouldComponentUpdate`).

The ClojureScript overhead is kept down, thanks to lots of caching.

Code size is a little bigger than React.js, but still quite small. The todomvc example clocks in at roughly 79K gzipped, using advanced compilation.

## About

The idea and some of the code for making components atom-like comes from [pump](https://github.com/piranha/pump).
The reactive-atom idea (and some code) comes from [reflex](https://github.com/lynaghk/reflex).

The license is MIT.
