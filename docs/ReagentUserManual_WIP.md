# Reagent User Manual

<!-- toc -->

* [Using Hiccup to Describe HTML](#using-hiccup-to-describe-html)
  * [Special treatment of `nil` child nodes](#special-treatment-of-nil-child-nodes)
  * [Special interpretation of `style` attribute](#special-interpretation-of-style-attribute)
  * [Special interpretation of `class` attribute](#special-interpretation-of-class-attribute)
  * [Special notation for nested elements](#special-notation-for-nested-elements)
  * [Rendering Hiccup](#rendering-hiccup)
* [Creating Components](#creating-components)
  * [What is a "Component"](#what-is-a-component)
  * [Core of a Component: the Render Function](#core-of-a-component-the-render-function)
  * [Three forms of components](#three-forms-of-components)
  * [Form-1: A Simple Function](#form-1-a-simple-function)
  * [Form-2: A Function Returning A Function](#form-2-a-function-returning-a-function)
  * [How Reagent Knows the Difference Between Form-1 and Form-2](#how-reagent-knows-the-difference-between-form-1-and-form-2)
  * [Form-3: A Class With Life Cycle Methods](#form-3-a-class-with-life-cycle-methods)
  * [All Three Component Forms Are the Same](#all-three-component-forms-are-the-same)
  * [with-let: Handling destruction](#with-let-handling-destruction)
  * [Keys](#keys)
* [How Reagent Renders](#how-reagent-renders)
  * [The difference between parenthesis and square brackets](#the-difference-between-parenthesis-and-square-brackets)
* [Managing state: atoms, cursors, Reactions, and tracking](#managing-state-atoms-cursors-reactions-and-tracking)
  * [Intro to atoms](#intro-to-atoms)
    * [Mutating a ratom](#mutating-a-ratom)
    * [Dereferencing a ratom](#dereferencing-a-ratom)
    * [The effect of dereferencing a ratom](#the-effect-of-dereferencing-a-ratom)
    * [rswap!](#rswap)
  * [Cursors](#cursors)
    * [More general cursors](#more-general-cursors)
  * [Reactions](#reactions)
  * [The track function](#the-track-function)
    * [The track! function](#the-track-function)
* [When Components Re-render](#when-components-re-render)
* [Batching and Timing: How Reagent Renders Changes to Application State](#batching-and-timing-how-reagent-renders-changes-to-application-state)
  * [The bad news](#the-bad-news)
  * [An example](#an-example)
  * [Tapping into the rendering loop](#tapping-into-the-rendering-loop)
* [Interop with React](#interop-with-react)
  * [Creating React Elements directly](#creating-react-elements-directly)
  * [Creating React Elements from Hiccup forms](#creating-react-elements-from-hiccup-forms)
  * [Creating Reagent "Components" from React Components](#creating-reagent-components-from-react-components)
  * [Creating React Components from Reagent "Components"](#creating-react-components-from-reagent-components)
  * [Example: "Decorator" Higher-Order Components](#example-decorator-higher-order-components)
  * [Example: Function-as-child Components](#example-function-as-child-components)
  * [Getting props and children of current component](#getting-props-and-children-of-current-component)
  * [React Interop Macros](#react-interop-macros)
* [Todo](#todo)

<!-- tocstop -->

# Using Hiccup to Describe HTML

Reagent uses a datastructure known as Hiccup to describe HTML. Hiccup describes HTML elements and user-defined components as a nested ClojureScript vector:

```clojure
[:div {:class "parent"}
  [:p {:id "child-one"} "I'm first child element."]
  [:p "I'm the second child element."]]
```

As described below, reagent provides a number of extensions and conveniences to Hiccup, but the general rules of Hiccup are as follows:

1. The first element is either a keyword or a symbol
   * If it is a keyword, the element is an HTML element where `(name keyword)` is the tag of the HTML element.
   * If it is a symbol, reagent will treat the vector as a component, as described in the next section.
2. If the second element is a map, it represents the attributes to the element. The attribute map may be omitted.
3. Any additional elements must either be Hiccup vectors representing child nodes or string literals representing child text nodes.

## Special treatment of `nil` child nodes

Reagent and React ignore nil nodes, which allow conditional logic in Hiccup forms:

```clojure
(defn my-div [child?]
  [:div
    "Parent Element"
    (when child? [:div "Child element"])])
```

In this example `(my-div false)` will evaluate to `[:div "Parent Element" nil]`, which reagent will simply treat the same as `[:div "Parent Element"]`.

## Special interpretation of `style` attribute

The `:style` attribute can be written a string or as a map. The following two are equivalent:

```clojure
[:div {:style "color: red; font-weight: bold"} "Alert"]
[:div {:style {:color "red"
               :font-weight "bold"}
      "Alert"]
```

The map form is the same as [React's style attribute](https://reactjs.org/docs/dom-elements.html#style), except that when using the map form of the style attribute, the keys should be the same name as the CSS attribute as shown in the above example (not camel cased as is required JavaScript).

## Special interpretation of `class` attribute

In JavaScript, `class` is a reserved keyword, so React uses the `className` to specify class attibutes. Reagent just uses `class`.

As of reagent 0.8.0, the `class` attribute accepts a collection of classes and will remove any nil value:

```clojure
[:div {:class ["a-class" (when active? "active") "b-class"]}]
```

## Special notation for nested elements

Reagent extends standard Hiccup in one way: it is possible to "squeeze" elements together by using a `>` character.

This:

```clojure
[:div
  [:p
    [:b "Nested Element"]]]
```

can be written as:

```clojure
[:div>p>b "Nested Element"]
```

## Rendering Hiccup

The primary entrypoint to the reagent library is `reagent.core/render`.

```clojure
(ns example
  (:require [reagent.core :as r]))

(defn render-simple []
  (r/render [:div [:p "Hello world!"]]
    (.-body js/document)))
```

A discussion of the details of rendering are found after we introduce components.

# Creating Components

In React, all components are first declared as objects or classes, and then instantiated using React.createElement or using JSX. Reagent lets you create React components too, but most kinds of webpages can be created with Reagent using standard ClojureScript functions.

## What is a "Component"

First a note about terminology. The term component has multiple meanings when using reagent. First, there is the underlying JavaScript React.Component class that React is built upon. You can create these in reagent directly using `reagent/create-class`. But apart from actual React components, you will find documentation (including the very next section of this guide) often refering to reagent render functions as "components." The dual use of this term can be confusing if you forget that most of the time, you will be dealing with these plain ClojureScript functions, which are often pure functions returning plain data.

Eventually, you will provide these render functions (described below as `Form-1` and `Form-2` components) to reagent via `reagent.core/render` or `reagent.core/as-element`, which will wrap them with state-tracking funcionality and turn them into React components. The function you provide will serve as the component's render function. And although it is convienent to refer to these render functions as "components," the more precise way to describe them is that they are normal ClojureScript functions that are used by the reagent library to create React components.

## Core of a Component: the Render Function

At the core of any component is a `render` function. A `render` function is the backbone, mandatory part of a component. In fact, as you'll soon see, components will often collapse down to be nothing more than a `render` function.

A `render` function turns data into Hiccup. Data is supplied via the function parameters, and Hiccup is the return value. Data in, Hiccup out.

## Three forms of components

There are three ways to create a `component`.

Ordered by increasing complexity, they are:

1. **via a simple render function** - data in as parameters, and it returns HTML.
2. **via a function which returns the render function** - the returned function is the render function.
3. **via a map of functions, one of which is the render** the rest of the functions are `React lifecycle` methods which allow for some more advanced interventions.

> In all three cases, a `render` function is provided -- that's the backbone. The three creation methods differ only in terms of what they supply over and above a `renderer`.

## Form-1: A Simple Function

In the simplest case, a `component` collapses down to _only_ be a `render` function. You supply nothing else. You just write a regular clojurescript function which takes data as parameters and produces a Hiccup vector.

```cljs
(defn greet
   [name]                    ;; data coming in is a string
   [:div "Hello " name])     ;; returns Hiccup representing HTML
```

**Components must return single nodes:** At some point, you'll probably try to return sibling HTML elements in a normal cljs vector:

```cljs
(defn wrong-component
   [name]
   [[:div "Hello"] [:div name]])     ;; a vec of 2 [:div]
```

That isn't valid Hiccup and you'll get a slightly baffling error. You'll have to correct this mistake by wrapping the two siblings in a parent [:div]:

```cljs
(defn right-component
   [name]
   [:div
     [:div "Hello"]
     [:div name]])     ;; [:div] containing two nested [:divs]
```

**TODO IS THIS POSSIBLE IN 0.8?**

## Form-2: A Function Returning A Function

Now, let's take one step up in complexity. Sometimes, a component requires:

* some setup; or
* some local state; and of course
* a renderer

The first two are optional, the last is not.

`Form-2` components are written as an `outer` function which returns an `inner` render.

```cljs
(defn timer-component []
  (let [seconds-elapsed (reagent/atom 0)]     ;; setup, and local state
    (fn []        ;; inner, render function is returned
      (js/setTimeout #(swap! seconds-elapsed inc) 1000)
      [:div "Seconds Elapsed: " @seconds-elapsed])))
```

Here `timer-component` is the outer function, and it returns an inner, anonymous render function which closes over the initialised, local state `seconds-elapsed`.

As before, the job of the render function is to turn data into Hiccup. That's the backbone. Its just that `Form-2` allows your renderer to close over some state created and initialised by the outer.

Let's be quite clear what is going on here:

* `timer-component` is **called once** per component instance (and will create the state for that instance)
* the render function it returns will potentially be called **many, many times**. In fact, it will be called each time Reagent detects a possible difference in that `component`'s inputs.

**Rookie mistake**

When starting out, everyone makes this mistake with the `Form-2` construct: they forget to repeat the parameters in the inner, anonymous render function.

```cljs
(defn outer
  [a b c]            ;; <--- parameters
  ;;  ....
  (fn [a b c]        ;; <--- forgetting to repeat them, is a rookie mistake
    [:div
      (str a b c)]))
```

So the rookie mistake is to forget to put in the `[a b c]` parameters on the inner render function.

Remember, `outer` is called once _per component instance_. Each time, the parameters to `outer` will hold the initial parameter values. The renderer on the other hand, will be called by Reagent many times and, each time, potentially with alternative parameter values, but unless you repeat the parameters on the renderer it will close over those initial values in `outer`. As a result, the component renderer will stubbornly only ever render the original parameter values, not the updated ones, which can be baffling for a beginner.

## How Reagent Knows the Difference Between Form-1 and Form-2

When Reagent is turning your Hiccup into React components, (see below for a fuller discussion of the rendering process), it will first invoke the form-1 or form-2 function specified. For example:

```clojure
(defn my-form1 [arg]
  [:div "Form 1 function " arg])
(defn my-form2 [arg]
  (let ([local-state ...]))
    (fn [arg]
      [:div "Form 2 function " arg]))
(defn render-simple []
  (r/render [:div [my-form1 "some data"]
                  [my-form2 "some data"]]
    (.-body js/document)))
```

In this example, the `my-form1` function will return a vector and the `my-form2` function will return a function. Reagent's render logic treats these differently.

* For the form-1 case, reagent creates a React component with `my-form1` as the render function. Reagent keeps track of the return value from `my-form1` so that it isn't called twice on initial render.

* For the form-2 case, reagent creates a React component with the function returned from `my-form2` as the render function. Note, this is why failing to repeat the function parameters in the interior function causes form-2 components never to update even when their props change: the argument **are** actually passed to the interior render function but the render function has bound its parameters to the outer function parameters instead of the newly passed arguments.

## Form-3: A Class With Life Cycle Methods

Most of the time, form-1 and form-2 components will be sufficient for all webpage needs. But sometimes you will need access to [React's lifecycle methods.](http://facebook.github.io/react/docs/component-specs.html#lifecycle-methods) Some typical reasons for this are interfacing with a `<canvas>` element or a library like D3.

**Note:** The function signatures for the lifecycle methods are the same as the React methods, except that `this` is passed as the first argument.

A `Form-3` component definition looks like this:

```cljs
(defn my-component
  [x y z]
  (let [some (local but shared state)       ;; <-- closed over by lifecycle fns
        can  (go here)]
     (reagent/create-class                  ;; <-- expects a map of functions
       {:component-did-mount                ;; the name of a lifecycle function
        (fn [this]
          (println "component-did-mount"))  ;; your implementation

        :component-will-mount               ;; the name of a lifecycle function
        (fn [this]
          (println "component-will-mount")) ;; your implementation

        ;; other lifecycle funcs can go in here

        :display-name  "my-component"  ;; for more helpful warnings & errors

        :reagent-render        ;; Note:  is not :render
         (fn [x y z]           ;; remember to repeat parameters...
            [:div (str x " " y " " z)])}))) ;; ...or x, y, and z will never change

(reagent/render-component
    [my-component 1 2 3]         ;; pass in x y z
    (.-body js/document))

;; or as a child in a larger Reagent component

(defn homepage []
  [:div
   [:h1 "Welcome"]
   [my-component 1 2 3]])
```

**Tip: use `reagent-render`, not `render`**

In the code sample above, notice that the renderer function is identified via an odd keyword in the map given to `reagent/create-class`. It's called `:reagent-render` rather than the shorter, more obvious `:render`.

Its a trap to mistakenly use `:render` because you won't get any errors, **except** the function you supply will only ever be called with one parameter, and it won't be the one you expect. [Some details here](https://github.com/reagent-project/reagent/issues/47#issuecomment-61056999).

**Note:** prior to version 0.5.0 you had to use the key `:component-function` instead of `:reagent-render`.

**Tip: specify `display-name`**

Leaving out the `:display-name` entry. If you leave it out, Reagent and React have no way of knowing the name of the component causing a problem. As a result, the warnings and errors they generate won't be as informative.

## All Three Component Forms Are the Same

Reagent users have adopted the terms `Form-1`, `Form-2` and `Form-3`, but there's actually only one kind of component. It is just that there's **3 different ways to create a component**.

At the end of the day, no matter how it is created, a component will end up with a render function and some life-cycle methods. A component created via `Form-1` has the same basic structure as one created via `Form-3` because underneath they are all just React components.

## with-let: Handling destruction

The with-let macro looks just like let – but the bindings only execute once, and it takes an optional finally clause, that runs when the component is no longer rendered.
For example: here's a component that sets up an event listener for mouse moves, and stops listening when the component is removed.

```clojure
(defn mouse-pos-comp []
  (r/with-let [pointer (r/atom nil)
               handler #(swap! pointer assoc
                               :x (.-pageX %)
                               :y (.-pageY %))
               _ (.addEventListener js/document "mousemove" handler)]
    [:div
     "Pointer moved to: "
     (str @pointer)]
    (finally
      (.removeEventListener js/document "mousemove" handler))))
```

The same thing could of course be achieved with React lifecycle methods, but that would be a lot more verbose.

with-let can also be combined with [track](#the-track-function) (and other Reactive contexts). For example, the component above could be written as:

```clojure
(defn mouse-pos []
  (r/with-let [pointer (r/atom nil)
               handler #(swap! pointer assoc
                               :x (.-pageX %)
                               :y (.-pageY %))
               _ (.addEventListener js/document "mousemove" handler)]
    @pointer
    (finally
      (.removeEventListener js/document "mousemove" handler))))

(defn tracked-pos []
  [:div
   "Pointer moved to: "
   (str @(r/track mouse-pos))])
```

The finally clause will run when mouse-pos is no longer tracked anywhere, i.e in this case when tracked-posis unmounted.

`with-let` can also generally be used instead of returning functions from components that keep local state, and may be a bit easier to read.

TODO: is this true? if so mention during form-2 components

## Keys

React will issue a warning if you do not provide a `key` attribute to a `seq` of components. Keys can be specified either by using meta-data or by simply providing it as a property.

* As meta-data (use for Reagent component or for native HTML elements)

```clojure
^{:key foo} [:li bar]
```

or

```clojure
(with-meta
 [my-component]
 {:key id}))
```

* As a property to a native HTML element

```clojure
[:li {:key foo} bar]
```

# How Reagent Renders

All reagent programs have mount code that looks something like this:

```clojure
(defn mount-root []
  (reagent/render [top-level-component]
  (.getElementById js/document "app")))
```

This render function expects one of two things:

1. A React Element, which will just be passed to React as is.
2. A ClojureScript vector (i.e., a Hiccup form).

If it encounters a ClojureScript vector, it will interpret it as Hiccup. Reagent expects one of two things in the first position of the vector:

1. A keyword like `:div` or `:span`, which it will create using React.createElement
2. A symbol like `my-component`.

If it is symbol, then reagent will evaluate a function by that name. Reagent expects one of three things from this function:

1. A Hiccup vector. Reagent creates a React component with the function as its render method and uses the Hiccup vector for the initial render.
2. A ClojureScript function. Reagent will then create a React component with this inner function as the render method and will then call the inner function for the initial render.
3. A React component. Reagent will render this using React.createElement. Note, this could be a result of calling (React.core/create-class) or it could be a React component you have imported from a JavaScript library.

## The difference between parenthesis and square brackets

This render algorithm hopefully makes clear the difference between returning a component in parenthesis vs. square brackets. It also shows why calling a very simple reagent function as a function can generate correct HTML:

```clojure
(defn simple-div []
  [:div "Hello"])

(defn my-app []
  [:div
    (simple-div)]) ;; Calling simple-div as a function instead of returning
                   ;; a vector containing the single symbol simple-div.
                   ;; Don't do this.

(defn mount-root []
  (reagent/render
  (.getElementById js/document "app")))
```

This only works here because the invocation of `simple-div` will return a Hiccup vector `[:div "Hello"]`, which the render function understands how to turn into a React Element.

Although the correct HTML is rendered, this is not a good practice.

1. The child component is always re-rendered whenever the parent component renders. If the parent had just returned a vector, the child would not be re-rendered unless its arguments changed.)
2. This will not work for form-2 components with local state. If `simple-div` above had local state, it would be rebound everytime `my-app` renders. If `my-app` had simply returned the vector `[simple-div]`, then reagent would know how to update `simple-div` when appropriate at runtime.
3. This is almost certainly not what you want for form-3 components either, as it will create and destroy the component on each render.

# Managing state: atoms, cursors, Reactions, and tracking

Although it is possible to update reagent componetns by remounting the entire component tree with `react.core/render`, Reagent comes with a sophisticated state management library based on `reagent.core/atom`, which allows components to track application state and update only when needed. Reagent also provides cursors, which are like ratoms but can be constructed from portions of one or more other ratoms to limit or expand which ratoms a component watches. Finally, Reagent provides a set of tracking primitives called reactions and a set of utility functions to build more customized state management. **TODO is this even close to right?**

## Intro to atoms

Reagent provides an implementation of atom that you can create with `reagent/atom` and use just like a normal Clojure atom, which are often referred to as "ratoms" to distinguish from normal atoms. Reagent tracks any dereferences to ratoms made during a component's render function.

```clojure
(ns example
  (:require [reagent.core :as r]))

(def click-count (r/atom 0))

(defn counting-component []
  [:div
   "The atom " [:code "click-count"] " has value: "
   @click-count ". "
   [:input {:type "button" :value "Click me!"
            :on-click #(swap! click-count inc)}]])
```

### Mutating a ratom

You manipulate using the standard `reset!` and `swap!` functions.

```clojure
(reset! state-atom {:counter 0})
```

```clojure
(swap! state-atom assoc :counter 15)
```

### Dereferencing a ratom

You access the atom using `deref` or the shorthand `@`.

```clojure
(:counter (deref state-atom))
(:counter @state-atom)
```

### The effect of dereferencing a ratom

* A dereference to the ratom during its render function will cause that component to re-render whenever any part of that ratom is updated. (See the section below on cursors to get finer control over update behavior.)
* Dereferencing a ratom in a callback or event handler after the render function has run will not make the component react to any changes to the ratom (though of course any _changes_ to the ratom made in an event handler will make any watching components re-render).

### rswap!

`rswap!` works like standard `swap!` except that it

* always returns `nil`
* allows recursive applications of `rswap!` on the same atom

That makes `rswap!` especially suited for event handling.

Here’s an example that uses event handling with `rswap!`:

```clojure
(defn event-handler [state [event-name id value]]
  (case event-name
    :set-name   (assoc-in state [:people id :name]
                          value)
    :add-person (let [new-key (->> state :people keys (apply max) inc)]
                  (assoc-in state [:people new-key]
                            {:name ""}))
    state))

(defn emit [e]
  ;; (js/console.log "Handling event" (str e))
  (r/rswap! app-state event-handler e))

(defn name-edit [id]
  (let [p @(r/track person id)]
    [:div
     [:input {:value (:name p)
              :on-change #(emit [:set-name id (.-target.value %)])}]]))

(defn edit-fields []
  (let [ids @(r/track person-keys)]
    [:div
     [name-list]
     (for [i ids]
       ^{:key i} [name-edit i])
     [:input {:type 'button
              :value "Add person"
              :on-click #(emit [:add-person])}]]))
```

All events are passed through the emit function, consisting of a trivial application of `rswap!` and some optional logging. This is the only place where application state actually changes – the rest is pure functions.

The actual event handling is done in event-handler, which takes state and event as parameters, and returns a new state (events are represented by vectors here, with an event name in the first position).

All the UI components have to do is then just to return some markup, and set up routing of events through the emit function.

This architecture basically divides the application into two logical functions:

* The first takes state and an event as input, and returns the next state.

* The other takes state as input, and returns a UI definition.

This simple application could probably just as well use the common `swap!` instead of `rswap!`, but using `swap!` in React’s event handlers may trigger warnings due to unexpected return values, and may cause severe headaches if an event handler called by emit itself emits a new event (that would result in lost events, and much confusion).

For a more structured version of a similar approach, see the excellent re-frame framework.

TODO: is this the right example for rswap? If this is just re-frame light, maybe this shouldn't be in here. It would be better to have a more concise example.

## Cursors

Any component that dereferences a state atom will update whenever any part of it is updated. If you are storing all state in a single atom (not uncommon), it will cause every component to update whenever the state is updated. Performance-wise, this may be acceptable, depending on how many elements you have and how often your state updates, because React itself will not manipulate the DOM unless the components actually change.

Reagent provides cursors, which behave like atoms but operate like pointers into a larger atom (or into multiple parts of multiple atoms).

Cursors are created with `reagent/cursor`, which takes a ratom and a keypath (like `get-in`):

```clojure
;; First create a ratom
(def state (reagent/atom {:foo {:bar "BAR"}
                          :baz "BAZ"
                          :quux "QUUX"}))
;; Now create a cursor
(def bar-cursor (reagent/cursor state [:foo :bar]))

(defn quux-component []
  (js/console.log "quux-component is rendering")
  [:div (:quux @state)])

(defn bar-component []
  (js/console.log "bar-component is rendering")
  [:div @bar-cursor])

(defn mount-root []
  (reagent/render [:div [quux-component] [bar-component]]
    (.getElementById js/document "app"))
  (js/setTimeout (fn [] (swap! state assoc :baz "NEW BAZ")) 1000)
  (js/setTimeout (fn [] (swap! state assoc-in [:foo :bar] "NEW BAR")) 2000))


;; Console output:
;; quux-component is rendering
;; bar-component is rendering
;; After 1 second:
;; quux-component is rendering
;; After 2 seconds:
;; quux-component is rendering
;; bar-component is rendering
```

Both `bar-component` and `quux-component` update whenever their respective cursors/atoms update, but because `bar-component`'s cursor is limited only to the relevant portion of the app-state, it only re-renders when `[:foo :bar]` updates, whereas `quux-component` updates each time `app-state` changes, even though `:quux` never changes.

### More general cursors

The cursor mechanism is more general than described above. You can pass a function that performs arbitrary transformations on one or more atoms. **TODO (DO WE NEED TO EXPLAIN THIS?)**

Reagent also provides the `reagent/wrap` mechanism, which also derives a new atom but provides more general functionality. Where a cursor will always update the atom from which it was derived, `reagent/wrap` takes an atom and a callback that will be called whenever the derived atom is updated. Replacing `(r/cursor n [:first-name])` with `(r/wrap first-name swap! n assoc :first-name)]` gives essentially the same results. **TODO (WHAT UTILITY DOES THIS HAVE?)**

## Reactions

Reactions are like cursors called with a function.

When reactions produce a new result (as determined by `=`), they cause other dependent reactions and components to update.

The function `make-reaction`, and its macro `reaction` are used to create a `Reaction`, which is a type that belongs to a number of protocols such as `IWatchable`, `IAtom`, `IReactiveAtom`, `IDeref`, `IReset`, `ISwap`, `IRunnable`, etc. which make it atom-like: ie it can be watched, derefed, reset, swapped on, and additionally, tracks its derefs, behave reactively, and so on.

Reactions are what give `r/atom`, `r/cursor`, and function `r/cursor` and `r/wrap` their power.

`make-reaction` takes one argument, `f`, and an optional options map. The options map specifies what happens to `f`:

* `auto-run` (boolean) specifies whether `f` run on change
* `on-set` and `on-dispose` are run when the reaction is set and unset from the DOM
* `derefed` **TODO unclear**

**TODO EXAMPLE**

Reactions are executed asynchronously, so be sure to call `flush` if you depend on reaction side effects.

## The track function

`reagent.core/track` takes a function, and optional arguments for that function, and gives a derefable (i.e "atom-like") value, containing whatever is returned by that function. If the tracked function depends on a Reagent atom, it is called again whenever that atom changes – just like a Reagent component function. If the value returned by `track` is used in a component, the component is re-rendered when the value returned by the function changes.

In other words, `@(r/track foo x)` gives the same result as `(foo x)` – but in the first case, foo is only called again when the atom(s) it depends on changes.

Here's an example:

```clojure
(ns example.core
  (:require [reagent.core :as r]))
(defonce app-state (r/atom {:people
                            {1 {:name "John Smith"}
                             2 {:name "Maggie Johnson"}}}))

(defn people []
  (:people @app-state))

(defn person-keys []
  (-> @(r/track people)
      keys
      sort))

(defn person [id]
  (-> @(r/track people)
      (get id)))

(defn name-comp [id]
  (let [p @(r/track person id)]
    [:li
     (:name p)]))

(defn name-list []
  (let [ids @(r/track person-keys)]
    [:ul
     (for [i ids]
       ^{:key i} [name-comp i])]))
```

Here, the name-list component will only be re-rendered if the keys of the :people map changes. Every name-comp only renders again when needed, etc.

Use of track can improve performance in three ways:

1. It can be used as a cache for an expensive function, that is automatically updated if that function depends on Reagent atoms (or other tracks, cursors, etc).
2. It can also be used to limit the number of times a component is re-rendered. The user of track is only updated when the function’s result changes. In other words, you can use track as a kind of generalized, read-only cursor.
3. Every use of track with the same arguments will only result in one execution of the function. E.g the two uses of `@(r/track people)` in the example above will only result in one call to the people function (both initially, and when the state atom changes).

_Note:_ Compared to reactions, `reagent.ratom/reaction` and `track` are similar. The main differences are that track uses named functions and variables, rather than depending on closures, and that you don’t have to manage their creation manually (since tracks are automatically cached and reused).

_Note:_ The first argument to track should be a named function, i.e not an anonymous one. Also, beware of lazy data sequences: don’t use deref (i.e ”@”) with the for macro, unless wrapped in doall (just like in Reagent components).

### The track! function

`track!` works just like track, except that the function passed is invoked immediately, and continues to be invoked whenever any atoms used within it changes.

For example, given this function:

```clojure
(defn log-app-state []
  (prn @app-state))
```

you could use `(defonce logger (r/track! log-app-state))` to monitor changes to app-state. `log-app-state` would continue to run until you stop it, using `(r/dispose! logger)`.

# When Components Re-render

At a high level, components re-render when either one of two things happen:

1. Their arguments (props) change
2. An atom or cursor they derefed changes

It is also possible to force a component to re-render by:

1. Calling `reagent/render` again
2. Calling `reagent/force-update-all`. (But when used with hot code reloading, be sure to read [this comment](https://github.com/reagent-project/reagent/issues/94#issuecomment-73564259).)

# Batching and Timing: How Reagent Renders Changes to Application State

Changes in application state (as represented by Reagent’s `atom`s) are not rendered immediately to the DOM. Instead, Reagent waits until the browser is ready to repaint the window, and then all the changes are rendered in one single go.

This is good for all sorts of reasons:

* Reagent doesn't have to spend time doing renderings that no one would ever see (because changes to application state happened faster than the browser could repaint).
* If two or more atoms are changed simultaneously, this now leads to only one re-rendering, and not two.
* The new code does proper batching of renderings even when changes to atoms are done outside of event handlers (which is great for e.g core.async users).
* Repaints can be synced by the browser with for example CSS transitions, since Reagent uses requestAnimationFrame to do the batching. That makes for example animations smoother.

In short, Reagent renders less often, but at the right times. For a much better description of why async rendering is good, see David Nolen’s [excellent explanation here.](http://swannodette.github.io/2013/12/17/the-future-of-javascript-mvcs/)

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

# Interop with React

A little understanding of what reagent is doing really helps when trying to use React libraries and reagent together. Be sure to read [the section on how reagent renders](#how-reagent-renders).

## Creating React Elements directly

The `reagent.core/create-element` function simply calls React's `createElement` function (and therefore, it expects either a string representing an HTML element or a React Component).

As an example, here are four ways to create the same element:

```clojure
(defn integration []
  [:div
   [:div.foo "Hello " [:strong "world"]]

   (r/create-element "div"
                     #js{:className "foo"}
                     "Hello "
                     (r/create-element "strong"
                                        #js{}
                                        "world"))

   (r/create-element "div"
                     #js{:className "foo"}
                     "Hello "
                     (r/as-element [:strong "world"]))

   [:div.foo "Hello " (r/create-element "strong"
                                        #js{}
                                        "world")]])
(defn mount-root []
  (reagent/render [integration]
    (.getElementById js/document "app")))
```

This works because `reagent/render` itself expects (1) a React element or (2) a Hiccup form. If passed an element, it just uses it. If passed a Hiccup, it creats a (cached) React component and then creates an element from that component.

## Creating React Elements from Hiccup forms

The `reagent.core/as-element` function creates a React element from a Hiccup form. In the previous section, we discussed how `reagent/render` expects either (1) a Hiccup form or (2) a React Element. If it encounters a Hiccup form, it calls `as-element` on it. When you have a React component that wraps children, you can pass Hiccup forms to it wrapped in `as-element`.

## Creating Reagent "Components" from React Components

The function `reagent/adapt-react-class` will turn a React Component into something that can be placed into the first position of a Hiccup form, as if it were a Reagent function. Take, for example the react-flip-move library and assume that it has been properly imported as a React Component called `FlipMove`. By wrapping FlipMove with `adapt-react-class`, we can use it in a Hiccup form:

```clojure
(defn top-articles [articles]
  [(reagent/adapt-react-class FlipMove)
   {:duration 750
    :easing "ease-out"}
   articles]
```

There is also a convenience mechanism `:>` (colon greater-than) that shortens this and avoid some parenthesis:

```clojure
(defn top-articles [articles]
  [:> FlipMove
   {:duration 750
    :easing "ease-out"}
   articles]
```

This is the equivalent JavaScript:

```clojure
const TopArticles = ({ articles }) => (
  <FlipMove duration={750} easing="ease-out">
    {articles}
  </FlipMove>
);
```

## Creating React Components from Reagent "Components"

The `reagent/reactify-component` will take a Form-1, Form-2, or Form-3 reagent "component". For example:

```clojure
(defn exported [props]
  [:div "Hi, " (:name props)])

(def react-comp (r/reactify-component exported))

(defn could-be-jsx []
  (r/create-element react-comp #js{:name "world"}))
```

A few points to note:

* `adapt-react-class` and `reactify-component` are not perfectly symmetrical, because `reactify-component` requires that the reagent component accept everything in a single props map, including its children.
* [reagent/children](#getting-props-and-children-of-current-component) is helpful here in extracting the children passed to a reactified component.

## Example: "Decorator" Higher-Order Components

Some React libraries use the decorator pattern: a React component which takes a component as an argument and returns a new component as its result. One example is the React DnD library. We will need to use both `adapt-react-class` and `reactify-component` to move back and forth between React and reagent:

```clojure
(defn react-dnd-component
  []
  (let [decorator (DragDropContext HTML5Backend)]
    [(reagent/adapt-react-class
       (decorator (reagent/reactify-component top-level-component)))]))
```

This is the equivalent javascript:

```clojure
import HTML5Backend from 'react-dnd-html5-backend';
import { DragDropContext } from 'react-dnd';

class TopLevelComponent {
  /* ... */
}

export default DragDropContext(HTML5Backend)(TopLevelComponent);
```

## Example: Function-as-child Components

Some React components expect a function as their only child. React autosizer is one such example.

```clojure
[(reagent/adapt-react-class AutoSizer)
 {}
 (fn [dims]
  (let [dims (js->clj dims :keywordize-keys true)]
   (reagent/as-element [my-component (:height dims)])))]
```

## Getting props and children of current component

Because you just pass argument to reagent functions, you typically don't need to think about "props" and "children" as distinct things. But reagent does make a distinction and it is helpful to understand this particularly when interoperating with native elements and React libraries.

Specifically, if the first argument to your reagent function is a map, that is assigned to `this.props` of the underlying reagent component. All other arguments are assigned as children to `this.props.children`.

When interacting with native React components, it may be helpful to access props and children, which you can do with `reagent.core/current-component`. This function returns an object that allows you retrieve the props and children passed to the current component.

Beware that `current-component` is only valid in component functions, and must be called outside of e.g event handlers and for expressions, so it’s safest to always put the call at the top, as in `my-div` here:

```clojure
(ns example
  (:require [reagent.core :as r]))

(defn my-div []
  (let [this (r/current-component)]
    (into [:div.custom (r/props this)]
          (r/children this))))

(defn call-my-div []
  [:div
    [my-div "Some text."]
    [my-div {:style {:font-weight 'bold}}
      [:p "Some other text in bold."]]])
```

## React Interop Macros

Reagent provides two utility macros `$` and `$!` for getting and setting javascript properties in a way that is safe for advanced compilation.

`($ o :foo)` is equivalent to `(.-foo o)`
`($ o foo arg1 arg2)` is the same as `(.foo o arg1 arg2)`

Similarly,

`($! o :foo 1)` is equivalent to `(set! (.-foo o) 1)`

Note, these are not necessary if your JavaScript library has an externs file or if externs inference is on and working.

# Todo

* Provide a "functionality over plain react" in the readme instead of the introduction lite.
  * Atom-based state management (with batched updates and special cases for input elements)
  * hiccup interpretation
  * function->component technique.
* Provide some guidance on state management
  * One big ratom, multiple ratoms, cursors, etc.
  * Maybe use that re-frame light example?
* Link to other art like om, om-next, fulcro, redux, sagas, mobx
* Review this list of things and see if we have covered everything
  * Mounting components
    * r/render - done
    * considerations to ensure proper reloading - placeholder above
  * State management
    * ratom
    * reaction
    * cursor
    * track
    * track!
    * wrap
  * React interop
    * create-element
    * adapt-react-class
    * [:> ...]
    * as-element
    * reactify-component
    * requiring react - how React is found
  * Performance
    * async rendering
    * flush
    * force-update-all
    * force-update
    * next-tick
    * after-update
  * Lifecycle methods
    * List of available lifecycle methods
    * Form-2 components vs Form-3 components
    * Accessing props in lifecycle methods
    * Cleaning up: with-let
    * How should-component-update works in Reagent
  * Accessing DOM nodes/backing instances
    * dom-node
    * callback refs
  * Render methods
    * Accessing props and children
    * Form-2 components: remembering to repeat arguments in the inner function
    * :render vs :reagent-render
  * Reagent-flavored hiccup
    * Nested element syntax: [:li.foo>a.bar]
    * Handling nil
    * Vectors and sequences are handled differently
    * Settinging keys through meta-data
    * How props case is handled (:on-change -> "onChange")
  * Handling input elements
    * Keeping cursor position
    * Latency hacks
    * Using 3rd party input elements (e.g. from Semantic UI React)
  * Component-local state
    * Component-local ratoms
    * Form-2 components
    * reagent.core/state and reagent.core/set-state
  * Advanced topics
    * Server-side rendering
