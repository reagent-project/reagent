# Managing state: atoms, cursors, Reactions, and tracking

Although it is possible to update reagent components by remounting the entire component tree with `reagent.dom/render`, Reagent comes with a sophisticated state management library based on `reagent.core/atom`, which allows components to track application state and update only when needed. Reagent also provides cursors, which are like ratoms but can be constructed from portions of one or more other ratoms to limit or expand which ratoms a component watches. Finally, Reagent provides a set of tracking primitives called reactions and a set of utility functions to build more customized state management.

**TODO is this right?**

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
    :set-name   (assoc-in state [:people id :name] value)
    :add-person (let [new-key (->> state :people keys (apply max) inc)]
              (assoc-in state [:people new-key] {:name ""}))
    state))

(defn emit [e]
  ;; (js/console.log "Handling event" (str e))
  (r/rswap! app-state event-handler e))

(defn name-edit [id]
  (let [p @(r/track person id)]
    [:div
     [:input {:value     (:name p)
          :on-change #(emit [:set-name id (.. % -target -value)])}]]))

(defn edit-fields []
  (let [ids @(r/track person-keys)]
    [:div
     [name-list]
     (for [i ids]
       ^{:key i} [name-edit i])
     [:input {:type     'button
              :value    "Add person"
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

**TODO: is this the right example for rswap? If this is just re-frame light, maybe this shouldn't be in here. It would be better to have a more concise example.**

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
  (rdom/render [:div [quux-component] [bar-component]]
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

The cursor mechanism is more general than described above. You can pass a function that performs arbitrary transformations on one or more atoms.
**TODO (DO WE NEED TO EXPLAIN THIS?)**

Reagent also provides the `reagent/wrap` mechanism, which also derives a new atom but provides more general functionality. Where a cursor will always update the atom from which it was derived, `reagent/wrap` takes an atom and a callback that will be called whenever the derived atom is updated. Replacing `(r/cursor n [:first-name])` with `(r/wrap first-name swap! n assoc :first-name)]` gives essentially the same results.
**TODO (WHAT UTILITY DOES THIS HAVE?)**

## Reactions

Reactions are like cursors called with a function.

When reactions produce a new result (as determined by `=`), they cause other dependent reactions and components to update.

The function `make-reaction`, and its macro `reaction` are used to create a `Reaction`, which is a type that belongs to a number of protocols such as `IWatchable`, `IAtom`, `IReactiveAtom`, `IDeref`, `IReset`, `ISwap`, `IRunnable`, etc. which make it atom-like: ie it can be watched, derefed, reset, swapped on, and additionally, tracks its derefs, behave reactively, and so on.

Reactions are what give `r/atom`, `r/cursor`, and `r/wrap` their power.

`make-reaction` takes one argument, `f`, and an optional options map. The options map specifies what happens to `f`:

* `auto-run` (boolean) specifies whether `f` runs on change
* `on-set` and `on-dispose` are run when the reaction is set and unset from the DOM
* `derefed` **TODO unclear**

Reactions are very useful when

* You need a way in which a component only updates based on part of the ratom state. (reagent/cursor can also be used for this scenario)
* When you want to combine two `ratoms` and produce a result
* You want the component to use some transformed value of `ratom`

Here's an example:
```clojure
 (def app-state (reagent/atom {:state-var-1 {:var-a 2
                                             :var-b 3}
                               :state-var-2 {:var-a 7
                                             :var-b 9}}))

 (def app-var2a-reaction (reagent.ratom/make-reaction
                          #(get-in @app-state [:state-var-2 :var-a])))


 (defn component-using-make-reaction []
   [:div
    [:div "component-using-make-reaction"]
    [:div "state-var-2 - var-a : " @app-var2a-reaction]])

```

The below example uses `reagent.ratom/reaction` macro, which provides syntactic sugar compared to 
using plain `make-reaction`:

```clojure
(let [username (reagent/atom "")
      password (reagent/atom "")
      fields-populated? (reagent.ratom/reaction (every? not-empty [@username @password]))]
 [:div "Is username and password populated ?" @fields-populated?])
```
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

_Note:_ The first argument to track should be a named function, i.e not an anonymous one. Also, beware of lazy data sequences: don’t use deref (i.e ”@”) with the for macro, unless wrapped in doall (just like in Reagent components, see [Derefs inside lazy sequences](#derefs-inside-lazy-sequences)).

### The track! function

`track!` works just like track, except that the function passed is invoked immediately, and continues to be invoked whenever any atoms used within it changes.

For example, given this function:

```clojure
(defn log-app-state []
  (prn @app-state))
```

you could use `(defonce logger (r/track! log-app-state))` to monitor changes to app-state. `log-app-state` would continue to run until you stop it, using `(r/dispose! logger)`.

## Caveats of reactive derefs

Components, reactions and tracks find their dependencies by recording which
ratoms (and cursors, reactions, tracks) are dereferenced while their function
runs. Only derefs that actually happen during that run are recorded, which has
some consequences.

### Derefs inside lazy sequences

In development builds, this warning means a deref happened while Reagent was
turning a lazy sequence into React elements:

```
Warning: Reactive deref not supported in lazy seq, it should be wrapped in doall
```

```clojure
(def items (r/atom ["a" "b"]))
(def selected (r/atom 0))

(defn item-list []
  [:ul
   (for [[i item] (map-indexed vector @items)]
     ^{:key i}
     [:li {:class (when (= i @selected) "selected")} item])])
```

`for` and `map` return lazy sequences. The body doesn't run when `item-list`
runs, but later, whenever something first walks the sequence. That is not
necessarily inside the component's reactive context. (A sequence passed to a
child component is realized in the child's render, for example.) Reagent can't
reliably attribute these derefs to the right component, so it doesn't support
them:

- In development builds, the derefs inside the sequence are **not** registered
  as dependencies, and the warning is printed. Above, `item-list` re-renders
  when `items` changes (deref outside the `for`), but not when `selected`
  changes.
- Production builds skip the check, so whether the deref is tracked depends on
  where the sequence happens to be realized. Don't rely on it.

Fix it by making sure the derefs run during the render function:

```clojure
;; 1. Deref outside the lazy sequence (usually the clearest, and the deref
;;    only runs once instead of once per item)
(defn item-list []
  (let [sel @selected]
    [:ul
     (for [[i item] (map-indexed vector @items)]
       ^{:key i}
       [:li {:class (when (= i sel) "selected")} item])]))

;; 2. Realize the sequence immediately with doall
(defn item-list []
  [:ul
   (doall
     (for [[i item] (map-indexed vector @items)]
       ^{:key i}
       [:li {:class (when (= i @selected) "selected")} item]))])

;; 3. Build the elements eagerly, e.g. with into
(defn item-list []
  (into [:ul]
        (map-indexed (fn [i item]
                       [:li {:class (when (= i @selected) "selected")} item]))
        @items))
```

Note that a vector of elements (e.g. the result of `mapv`) can't be used as a
child directly, because Reagent would read it as a Hiccup form. Use `into` to
add the elements to the parent, as above.

The same applies to the functions given to `track` and `reaction`.

### Conditional derefs in reactions

A reaction (or track) only depends on what it dereferenced the last time it
ran. A ratom or reaction that is dereferenced only in some branch becomes a
dependency the first time that branch runs. Because of how Reagent orders
updates, a reaction that started depending on another reaction this way can
run *before* that reaction has been updated, and see its previous value. The
reaction then runs again with the up-to-date value, so the final value is
correct, but the intermediate run sees an inconsistent state ("glitch").

Example, from [issue #568](https://github.com/reagent-project/reagent/issues/568):

```clojure
(def x (r/atom 0))
(def a (r/reaction (+ @x 10)))
(def b (r/reaction (- @a 10)))   ;; always equal to @x

(def z (r/reaction
         (let [x @x]
           (if (> x 0)
             (let [b @b]
               ;; x and b should always be equal here, but they aren't
               (+ x b))
             x))))

(defn app []
  [:span @z])
```

Calling `(swap! x inc)` repeatedly, with `app` mounted, the `(> x 0)`
branch sees these `[x b]` pairs:

```
[1 1] [2 1] [2 2] [3 2] [3 3] ...
```

After the first change, every change runs `z` twice: first with the old value
of `b`, then with the new one. The final value of `z` (and so what `app`
renders) is correct, but the intermediate run is a problem if the reaction has
side effects, or throws when given inconsistent values.

Component render functions are not affected in the same way: re-renders are
batched and run after the queued reactions have run, so a render function sees
the settled values.

The workaround is to deref the value unconditionally, outside the condition:

```clojure
(def z (r/reaction
         (let [x @x
               b @b]
           (if (> x 0)
             (+ x b)
             x))))
```

Now `b` is a dependency from the first run, and `z` sees `[1 1] [2 2] [3 3] ...`.
The trade-off is an extra dependency: `z` is also re-run when `b` changes while
the condition is false and `b` isn't needed. Usually that is cheap, but keep it
in mind if the reaction is expensive or `b` changes often.
