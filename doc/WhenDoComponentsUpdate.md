In this, more intermediate, Reagent tutorial, we delve into the question: when do Components re-render and why?  

## Components Are Reactive

Reagent Components are "reactive" in the following way:
  - each Component has a render function 
  - this render function turns `input data` into `hiccup`  (HTML)
  - render functions are **rerun** when their `input data` changes, producing new hiccup
  - that new hiccup is "interpreted" by Reagent and ultimately results in new HTML

It is this whole **re-running** the renderer function thing that makes a Component reactive. It "reacts" to changes in its "inputs", producing a new output.

This page is about understanding how and why these reactions happen. 

### Reactive To What?

We start by looking at the `inputs` to the process.  What things, when they change value, trigger a re-run of a Component's renderer?

Short answer is that there's two kinds of `input data`: 
  - `props`
  - `ratoms`  

As we'll soon see, these two kinds of input are not quite equal. There are differences in the way they trigger.

## 1. Props

The first of these `inputs` is called `props`.

Consider this example Component:
```clj
(defn greet
  [name]          ;; name is a string            
  [:div "Hello " name])
```

`name` is a `prop` (short for property).  In this example, it is a string value.  In our clojurescript/Reagent world, it takes the form of a parameter to the Component renderer, `greet`. 

Each time the value of `name` changes over time, `greet` will rerender. 

Wait, what? How exactly can the value of `name` change over time - isn't it just a parameter? Don't parameters only ever get one value, when the function is called?

Well, you'll remember from previous tutorials that `greet` is going to be "promoted" to be the render function of a Component. As a Component renderer, it will get called at least once, but probably many, many times. So there will be the opportunity for `name` to have a different value each time `greet` is called and, in that sense, it is a value which can change over time.

To understand further, imagine we had a parent Component, which uses `greet`:
```clj
(defn greet-family
  [] 
  [:div 
    [greet "Dad"]
    [greet (str "Bro-" (rand-int 10))]])
```

When Reagent interprets the hiccup returned by `greet-family`, it will create 3 further components:  

  - a `:div` component, with two `greet` children
  - the 1st `greet` child will always be given the `name` "Dad". Always the same prop
  - the 2nd `greet` child will likely have a different value for `name` each time that `greet-family` renders. Perhaps "Bro-1" one time and "Bro-5" the next. Only 1 time in 10 will it be the same as last time.

After a Component's renderer runs and produces hiccup, Reagent interprets it. When it processes the output of `greet-family`,  it will check to see if these 3 rerendered Components themselves need rerendering.  The test Reagent uses is a simple one: for each Component, are the newly supplied `props` different to those supplied in the last render. Have they "changed"? 

If the `props` are different, then that Component's render will be called to create new hiccup.  But if the `props` to that Component are the same as last time, then no need to rerender it. 

Obviously, the `[greet "Dad"]` component is rendered by `greet-family` the same way each time, and will get the same `props` every time and, so, it will not need re-rendering.  It will render once, at the beginning, but never again, no matter how many times its parent `greet-family` is rerendered. 

On the other hand, `[greet (str "Bro-" (rand-int 10))]` will often render a different `name` prop. So, if `greet-family` rerenders, then that child component will often re-render too ... although about 1 time in 10 the prop this time will be the same as last time, and Reagent will determine that it doesn't need to be rerendered.

Which means we can now answer the question posed above - how can the value of `name` change over time for a given `greet` component?  Answer: when the parent Component re-renders, and supplies a new value as the `prop`.  

`props` flow from the parent. **A Component can't get new `props` unless its parent rerenders.** 

## 2. Ratoms

Let's now discuss the 2nd form of `input data` to a Component.  

This example is a bit contrived, but bear with me ...
```clj
(def name  (reagent.ratom/atom "Bear"))

(defn ask-for-forgiveness
  []           ;; <--- no props     
  [:div "Please " @name " with me"])   ;; notice that @
```

We can see that `ask-for-forgiveness` will return the hiccup  `[:div "Please " "Bear" " with me"]`

Well, initially anyway, because initially `name` contains the string value "Bear".

Data is flowing into the render function via this `name` ratom.  Reagent will detect that this renderer has a ratom input, and it will watch that ratom for changes.

If I suddenly got all scientific, and did this `(reset! name "Ursidae")`, Reagent would detect the change in `name`, and it would re-run any Component renderer which is dependent upon it.  That means `ask-for-forgiveness` is re-run, producing the new hiccup `[:div "Please " "Ursidae" " with me"]`.

Just so we're clear: a "data input" changes (the value in a ratom) and, then, the renderer is rerun to produce new hiccup.  The Component is reactive to the ratoms it derefs.

## A Combination

So that was the basics.  

Let's now look at how these things can combine. We're going to consider a case involving two child components, and a parent. 

Child Component 1:
```clj
(defn greet-number
  "I say hello to an integer"
  [num]                             ;; an integer
  [:div (str "Hello #" num)])       ;; [:div "Hello #1"]
```

Child component 2: 
```clj
(defn more-button
  "I'm a button labelled 'More' which increments counter when clicked"
  [counter]                                ;; a ratom
  [:div  {:class "button-class"
          :on-click  #(swap! counter inc)} ;; increment the int value in counter
   "More"])    
```

And, finally, a Form-2 parent Component which uses these two child components:
```clj
(defn parent
  [] 
  (let [counter  (reagent.ratom/atom 1)]    ;; the render closes over this state
    (fn  parent-renderer 
      []
      [:div 
        [greet-number @counter]      ;; notice the @. The prop is an int
        [more-button counter]])))    ;; no @ on counter
```

With this setup, answer this question: what rerendering happens each time the `more-button` gets clicked and `counter` gets incremented? 

Don't read on. Test yourself. Spend 30 seconds working it out.

Answer:
  1. Reagent will notice that `counter` has changed and that is an `input ratom` to `parent-renderer`, and it will rerun that renderer. 
  2. Reagent will interpret the hiccup returned by `parent-renderer`, and it will determine that a new (integer) prop has been supplied in the `[greet-number @counter]` Component, and it will then rerender that component too. 

Wait. Is that it?  Why doesn't the `[more-button counter]` component rerender too?  After all, its `prop` `counter` has changed???

No, I promise it won't rerender. But why not?  The answer is a bit subtle.

You see, `counter` itself hasn't changed. It is still the same ratom it was before. The value **in** `counter` has been incremented, but `counter` itself is still the same ratom.  So from Reagent's point of view `[more-button counter]` involves the same `prop` as "last time" and it concludes that there's no need for a rerender of that component. 

Had `more-button` dereferenced the `counter` ratom THEN the change in `counter` should have triggered a rerender of `more-button`.  But if you look at `more-button` you'll see no `@counter`. There is no dereference.

If you truly understand this example, then you've gone a long way to officially getting it. 

## Different 

Although they are both ways to trigger a reactive re-render, the two kinds of `inputs` have different properties: 
  1. the definition of "changed" applied
  2. treatment of lifecycle functions

## Changed?

Till now, I've said a renderer will be re-run when an input value "changed".  But I've been carefully avoiding any definition of "changed".

You see, there's at least two definitions: `=` and `identical?`

```
(def x1  {:a 42  :b 45})    ;; at time 1, x has this value
(def x2  {:a 42  :b 45})    ;; at time 2, x has this value

(= x1 x2)                   ;; is x the same, or has it changed? 
;; =>  true                 ;; answer: no change

(identical? x1 x2)          ;; is x the same, or has it changed?
;; => false                 ;; answer: different
```

So we can see different answers to the question "has x changed?" for the same values, depending on the function we use.

For `props`,  `=` is used to determine if a new value have changed with regard to an old value. 

For ratoms, `identical?` is used (on the value inside the ratom) to determine if a new value has changed with regard to an old value. 

So, it is only when values are deemed to have "changed", that a re-run is triggered, but the inputs use different definitions of "changed".  This can be confusing. 

The `identical?` version is very fast. It is just a single reference check.

The `=` version is more accurate, more intuitive, but potentially more expensive. Although, as I'm writing this I notice that `=` uses `identical?` [when it can](https://github.com/clojure/clojurescript/blob/1b7390450243693d0b24e8d3ad085c6da4eef204/src/main/cljs/cljs/core.cljs#L1108-L1124).

**Update:**

> As of Reagent 0.6.0, ratoms use `=` (instead of `identical?`) is to determine if a new value is different to an old value. So, `ratoms` and `props` now have the same `changed?` semantics. 

### Efficient Re-renders

It's only via rerenders that a UI will change.  So re-rendering is pretty essential.  

On the other hand, unnecessary re-rendering should be avoided.  In the worst case, it could lead to performance problems.  By unnecessary rendering, I mean rerenders which result in unchanged HTML. That's a whole lot of work for no reason.

So this notion of "changed" is pretty important.  It controls if we are doing unnecessary, performance-sapping re-rendering work. 

### Lifecycle Functions

When `props` change, the entire underlying React machinery is engaged. Reagent Components can have lifecycle methods like `component-did-update` and these functions will get called, just as they would if you were dealing with a React Component. 

But ... when the re-render occurs because an input ratom changed, **Lifecycle functions are not run**.  So, for example, `component-did-update` will not be called on the Component. 

Careful of this one. It trips people up.


## Appendix 1

In the previous Tutorial, we looked at the difference between `()` and `[]`. 

Towards the end, I claimed that using `[]` was more efficient at "re-render time".  Hopefully, after the tutorial above, our knowledge is a bit deeper and we can now better appreciate the truth in this claim. 

Remember this code from the previous tutorial:
```clj
(defn greet-family-square
  [member1 member2 member3]
  [:div
    [greet member1]     ;; using [] not ()
    [greet member2]     
    [greet member3]])  
```

Now, imagine it used like this:
```clj
 ;; the 3rd member of the family to greet
(def extra (reagent.core/atom "Aunt Edith"))  

(defn top-level-component
    []
    [greet-family-square  "Mum" "Dad" @extra])

(reagent.core/render [top-level-component] (.-body js/document)))
```

The first time the page is rendered, the DOM created will greet three cherished people.  All good.  At this point, the `round` and `square` versions of `greet-family` would be equally good at getting the initial DOM into our browser.

But then, out of nowhere, comes information that our rich and eccentric Uncle John is rewriting his Last Will And Testament, and we need a fast, realtime change in our page. Luckily we have a repl handy, and we type `(reset! extra "Uncle John")`.  We've changed that `extra` r/atom which holds the 3rd cherished family member - sorry "Aunt Edith", you're out. 

What happens next?

1. Reagent will recognize that a `top-level-component` component relies on `extra` which has changed.  
2. So it will rerender that component. In the hiccup produced (by the rerender), it will see that `greet-family-square` has a new 3rd prop. And, by that, I mean that the value for the 3rd prop ("Uncle John") will not compare `=` to the value last rendered ("Aunt Edith"). 
3. So Reagent will trigger a rerender of `greet-family-square` with the new props ("Mum" "Dad" and "Uncle John")
4. In the hiccup produced by this rerender, Reagent will notice that the first two `greet` components have the same prop as that last rendered ("Mum" and "Dad") but that the 3rd `greet` component has a new prop value ("Uncle John"). 
5. So it will NOT rerender the first two `greet`, but the renderer for the 3rd will be rereun. 
 
As you can see, only the right parts of the tree are re-rendered.  Nothing unnecessary is done.

In the alternative `greet-family-round` version we looked at, the one which used `()` instead of `[]`, that efficiency is not possible. 

A re-render of `greet-family-round` always triggers three calls to `greet`, no matter what, accumulating a large amount of hiccup for `greet-family-round` to return.  It would then be left to React to diff all this new DOM with existing DOM and for it to work out that, in fact, parts of the tree (the first two greet parts) remain the same, and should be ignored. Which is a whole lot of unnecessary work!

When we use `[]`, we get independent React Components which will only be re-rendered if their `props` change (or ratoms change). More efficient, more minimal re-renderings.
