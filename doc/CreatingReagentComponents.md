
In reagent, the fundamental building block is a `component`.

Your reagent app will typically have many `components` - say, more than 5, but less than 100 - and the overall UI of a reagent app is the stitched-together-output from all of them, each contributing part of the overall HTML, typically in a hierarchical arrangement.

So they're important and this document describes how to create them.

##### Not An Absolute Introduction

Although I stay as basic as possible, this document isn't an introductory tutorial. You should read it *after*
you have already digested [The Official Introduction.](http://reagent-project.github.io/)

This document is useful because it clarifies the basics. It represents an extra bit of learning which might save you from some annoying paper cuts.

##### Contains Lies And Distortions

I care more about providing a useful mental model than bogging down with the full truth. Some white lies and distortions follow.

## The Core Of A Component

At the core of any `component` is a `render` function.

A `render` function is the backbone, mandatory part of a `component`.  In fact, as you'll soon see, `components` will often collapse down to be nothing more than a `render` function.

A `render` function turns data into HTML.  Data is supplied via the function parameters, and HTML is the return value.

Data in, HTML out.

Much of the time, a `render` function will be a `pure function`. If you pass the same data into a `render function`, then it will return the same HTML, and it won't side effect.

*Note*: ultimately, the surrounding reagent/React framework will cause non-pure side-effects because the returned HTML will be spliced into the DOM (mutating global state!), but here, for the moment, all we care about is the pureness of the `render` function itself)

## The Three Ways

There are three ways to create a `component`.

Ordered by increasing complexity, they are:
   1. **via a simple render function** - data in as parameters, and it returns HTML.
   2. **via a function which returns the render function** - the returned function is the render function.
   3. **via a map of functions, one of which is the render** the rest of the functions are `React lifecycle` methods which allow for some more advanced interventions.

> In all three cases, a `render` function is provided -- that's the backbone. The three creation methods differ only in terms of what they supply over and above a `renderer`.

## Form-1: A Simple Function

In the simplest case, a `component` collapses down to *only* be a `render` function.  You supply nothing else.

Although a simple approach, in my experience, you'll probably use `Form-1` components about 40% of the time, perhaps more.  Simple and useful.

You just write a regular clojurescript function which takes data as parameters and produces HTML.
```cljs
(defn greet
   [name]                    ;; data coming in is a string
   [:div "Hello " name])     ;; returns Hiccup (HTML)
```

Until now, I've talked about `render functions` returning HTML.  That isn't strictly speaking true, of course, as you've seen in the [The Offical Introduction.](http://reagent-project.github.io/)  Instead, renderers always return clojurescript data structures which specify HTML via `Hiccup` format.

`Hiccup` uses vectors to represent HTML elements, and maps to represent an element's attributes.

So this clojurescript data structure:
```cljs
[:div {:style {:background "blue"}} "hello " "there"]
```
is simply a clojurescript vector, containing a keyword, map and two strings. But when processed as `hiccup`, this data structure will produce the HTML:
```
<div style="background:blue;">hello there</div>
```
To understand more about Hiccup see [this Wiki.](https://github.com/weavejester/hiccup/wiki)

**Rookie mistake**

At some point, you'll probably try to return sibling HTML elements in a normal cljs vector:

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

Alternatively, you could return a [React Fragment](https://reactjs.org/docs/fragments.html). In reagent, a React Fragment is created using the `:<>` Hiccup form.

```cljs
(defn right-component
   [name]
   [:<>
     [:div "Hello"]
     [:div name]])
```

Referring to the example in [React's documentation](https://reactjs.org/docs/fragments.html), the `Columns` component could be defined in reagent as:

```cljs
(defn columns
  []
  [:<>
    [:td "Hello"]
    [:td "World"]]
```

## Form-2:  A Function Returning A Function

Now, let's take one step up in complexity.  Sometimes, a component requires:
  - some setup; or
  - some local state; and of course
  - a renderer

The first two are optional, the last is not.

`Form-2` components are written as an `outer` function which returns an `inner` render.

This example is taken from the tutorial:
```cljs
(defn timer-component []
  (let [seconds-elapsed (reagent/atom 0)]     ;; setup, and local state
    (fn []        ;; inner, render function is returned
      (js/setTimeout #(swap! seconds-elapsed inc) 1000)
      [:div "Seconds Elapsed: " @seconds-elapsed])))
```

Here `timer-component` is the outer function, and it returns an inner, anonymous render function which closes over the initialised, local state `seconds-elapsed`.

As before, the job of the render function is to turn data into HTML. That's the backbone. Its just that `Form-2` allows your renderer to close over some state created and initialised by the outer.

In my experience, you'll use `Form-2` `components` at least 50% of the time.

Let's be quite clear what is going on here:
  - `timer-component` is **called once** per component instance (and will create the state for that instance)
  - the render function it returns will potentially be called **many, many times**. In fact, it will be called each time Reagent detects a possible difference in that `component`'s inputs.

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

Remember, `outer` is called once _per component instance_.  Each time, the parameters to `outer` will hold the initial parameter values.  The renderer on the other hand, will be called by Reagent many times and, each time, potentially with alternative parameter values, but unless you repeat the parameters on the renderer it will close over those initial values in `outer`. As a result, the component renderer will stubbornly only ever render the original parameter values, not the updated ones, which can be baffling for a beginner.


## Form-3: A Class With Life Cycle Methods

Now, for the final step in complexity.

In my experience, you'll probably use `Form-3` `components` less than 1% of the
time, perhaps only when you want to use a js library like D3 or introduce some
hand-crafted optimisations. Maybe.  While you'll ignore `Form-3` components
most of the time, when you do need them, you need them bad. So pay attention,
because they'll save your bacon one day.

While the critical part of a component is its render function, sometimes we
need to perform actions at various critical moments in a component's lifetime,
like when it is first created, or when its about to be destroyed (removed from
the DOM), or when its about to be updated, etc.

With `Form-3` components, you can nominate `lifecycle methods`. reagent
provides a very thin layer over React's own `lifecycle methods`. So, before
going on, [read all about React's lifecycle
methods.](https://reactjs.org/docs/react-component.html#the-component-lifecycle).

Because React's lifecycle methods are object-oriented, they presume the ability
to access `this` to obtain the current state of the component.  Accordingly,
the signatures of the corresponding Reagent lifecycle methods all take a
reference to the reagent component as the first argument.  This reference can
be used with `r/props`, `r/children`, and `r/argv` to obtain the current
props/arguments.  There are some unexpected details with these functions
described below.  You may also find `r/dom-node` helpful, as a common use of
form-3 components is to draw into a `canvas` element, and you will need access
to the underlying DOM element to do so.

A `Form-3` component definition looks like this:
```cljs
(defn my-component
  [x y z]
  (let [some (local but shared state)      ;; <-- closed over by lifecycle fns
        can  (go here)]
     (reagent/create-class                 ;; <-- expects a map of functions
       {:display-name  "my-component"      ;; for more helpful warnings & errors

        :component-did-mount               ;; the name of a lifecycle function
        (fn [this]
          (println "component-did-mount")) ;; your implementation

        :component-did-update              ;; the name of a lifecycle function
        (fn [this old-argv]                ;; reagent provides you the entire "argv", not just the "props"
          (let [new-argv (rest (reagent/argv this))]
            (do-something new-argv old-argv)))

        ;; other lifecycle funcs can go in here


        :reagent-render        ;; Note:  is not :render
         (fn [x y z]           ;; remember to repeat parameters
            [:div (str x " " y " " z)])})))

(reagent/render
    [my-component 1 2 3]         ;; pass in x y z
    (.-body js/document))

;; or as a child in a larger Reagent component

(defn homepage []
  [:div
   [:h1 "Welcome"]
   [my-component 1 2 3]]) ;; Be sure to put the Reagent class in square brackets to force it to render!
```

Note the `old-argv` above in the signature for `component-did-update`.  Many of these Reagent lifecycle method analogs take `prev-argv` or `old-argv` (see the docstring for `reagent/create-class` for a full listing).  These `argv` arguments include the component constructor as the first argument, which should generally be ignored.  This is the same format returned by `(reagent/argv this)`.

Alternately, you can use `(reagent/props this)` and `(reagent/children this)`, but, conceptually, these don't map as clearly to the `argv` concept.  Specifically, the arguments to your render function are actually passed as children (not props) to the underlying React component, **unless the first argument is a map.**   If the first argument is a map, then that map is passed as props, and the rest of the arguments are passed as children.  Using `props` and `children` may read a bit cleaner, but you do need to pay attention to whether you're passing a props map or not.

Finally, note that some React lifecycle methods take `prevState` and `nextState`.  Because Reagent provides its own state management system, there is no access to these parameters in the lifecycle methods.

It is possible to create `Form-3` `components` using `with-meta`.  However, `with-meta` is a bit clumsy and has no advantages over the above method, but be aware that an alternative way exists to achieve the same outcome.

**Rookie mistake**

In the code sample above, notice that the renderer function is identified via an odd keyword in the map given to `reagent/create-class`. It's called `:reagent-render` rather than the shorter, more obvious `:render`.

Its a trap to mistakenly use `:render` because you won't get any errors, **except** the function you supply will only ever be called with one parameter, and it won't be the one you expect. [Some details here](https://github.com/reagent-project/reagent/issues/47#issuecomment-61056999).

**Rookie mistake**

While you can override `component-should-update` to achieve some performance improvements, you probably shouldn't unless you really, really know what you are doing. Resist the urge. Your current performance is just fine. :-)

**Rookie mistake**

Leaving out the `:display-name` entry.  If you leave it out, Reagent and React have no way of knowing the name of the component causing a problem. As a result, the warnings and errors they generate won't be as informative.

*****************

## Final Note

Above I used the terms `Form-1`, `Form-2` and `Form-3`, but there's actually only one kind of component. It is just that there's **3 different ways to create a component**.

At the end of the day, no matter how it is created, a component will end up with a render function and some life-cycle methods.  A component created via `Form-1` has the same basic structure as one created via `Form-3` because underneath they are all [just React components](https://betweentwoparens.com/blog/what-the-reagent-component!/).

## Appendix A - Lifting the Lid Slightly

Here's some further notes about Reagent's mechanics:
  1. When you provide a function as the first element of a hiccup vector `[my-func 1 2 3]`, Reagent will say "hey I have been given a render function".  That function might be `Form-1` or `Form-2`, but it doesn't know at that point. It just sees a function.
  2. A render function by itself is not enough to make a React Component.  So, Reagent takes this render function and "merges" it with default lifecycle functions to form a React component.  (`Form-3`, of course, allows you to supply your own lifecycle functions)
  3. Some time later, when Reagent **first** wants to render this component, it will, unsurprisingly, call the render function which you supplied (`my-func` in the snippet above).  It will pass in the "props" (parameters) supplied by the rendering parent (`1 2 3` in the snippet above).
  4. If this first call to the render function returns hiccup (a vector of stuff):
     - Reagent will just interpret it. So this is what happens in the case of a `Form-1` function.
     - If, however, this render function returns another function - ie. it is a `Form-2` outer function returning the inner function - then Reagent knows to replace the Component's render function with the newly returned inner function forever thereafter. So the outer will have been called once but, from that point forward, the inner function will be used for all further rendering. In fact,  Reagent will instantly call the inner function after the outer returns it, because Reagent wants a first rendering (hiccup) for the component.
  5. So, in the case of `Form-2`, the outer function is called once and once only (with initial props/parameters), and the inner is called at least once (with initial props/parameters), but probably many, many times thereafter. Both will be called with the same arrangement of props/parameters - although the inner render function will see different values in those props/parameters, over time.

## Appendix B - with-let macro

The `with-let` macro looks just like `let` – but the bindings **only execute once**, and it takes an optional `finally` clause, that runs when the component is no longer rendered. This can be particularly useful because it can prevent the need for a form-2 component in many instances (like creating a local reagent atom in your component).

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

`with-let` can also be combined with `track` (and other Reactive contexts). For example, the component above could be written as:

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

The `finally` clause will run when mouse-pos is no longer tracked anywhere, i.e in this case when tracked-pos is unmounted.
