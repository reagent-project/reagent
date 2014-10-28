
# Reagent

A simple [ClojureScript](http://github.com/clojure/clojurescript) interface to
[React](http://facebook.github.io/react/).

Reagent provides a way to write efficient React components using (almost) nothing but plain ClojureScript functions.

  * **[Detailed intro with live examples](http://holmsand.github.io/reagent/)**
  * **[News](http://holmsand.github.io/reagent/news/index.html)**

To use Reagent you add this to your dependencies in `project.clj`:

    [reagent "0.4.3"]

You also need to include react.js itself. One way to do this is to add

    :preamble ["reagent/react.js"]

to the *:compiler* section of project.clj, as shown in the examples
directory (or "reagent/react.min.js" in production). You could also
add

    <script src="http://fb.me/react-0.9.0.js"></script>

directly to your html.


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
  (reagent/render-component [childcaller]
                            (.-body js/document)))
```

assuming we have imported Reagent like this:

```clj
(ns example
  (:require [reagent.core :as reagent :refer [atom]]))
```

State is handled using Reagent's version of `atom`, like this:

```clj
(def click-count (atom 0))

(defn state-ful-with-atom []
  [:div {:on-click #(swap! click-count inc)}
   "I have been clicked " @click-count " times."])
```

Any component that dereferences a `reagent.core/atom` will be automatically re-rendered.

If you want do some setting up when the component is first created, the component function can return a new function that will be called to do the actual rendering:

```clj
(defn timer-component []
  (let [seconds-elapsed (atom 0)]
    (fn []
      (js/setTimeout #(swap! seconds-elapsed inc) 1000)
      [:div
       "Seconds Elapsed: " @seconds-elapsed])))
```

This way you can avoid using React's lifecycle callbacks like `getInitialState` and `componentWillMount` most of the time.

But you can still use them if you want to, either using `reagent.core/create-class` or by attaching meta-data to a component function:

```clj
(def my-html (atom ""))

(defn plain-component []
  [:p "My html is " @my-html])

(def component-with-callback
  (with-meta plain-component
    {:component-did-mount
     (fn [this]
       (reset! my-html (.-innerHTML (reagent/dom-node this))))}))
```

See the examples directory for more examples.


## Performance

React is pretty darn fast, and so is Reagent. It should even be faster than plain old javascript React a lot of the time, since ClojureScript allows us to skip a lot of unnecessary rendering (through judicious use of React's `shouldComponentUpdate`).

The ClojureScript overhead is kept down, thanks to lots of caching.

Code size is a little bigger than React.js, but still quite small. The todomvc example clocks in at roughly 53K gzipped, using advanced compilation.


## About

The idea and some of the code for making components atom-like comes from [pump](https://github.com/piranha/pump). The reactive-atom idea (and some code) comes from [reflex](https://github.com/lynaghk/reflex).

The license is MIT.
