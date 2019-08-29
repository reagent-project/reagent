This is a quick tutorial regarding the use of `()` and  `[]` in Reagent renderers.

Reagent is a terrific library.  You get going fast and your intuitions about how things should work seem to largely match how stuff actually does work.  It is all a bit magic and easy. 

But, eventually, you'll want to understand how that magic happens. Otherwise ... one day, 
in the peace of a shower, or on that serene bike ride to work, your eyes will flick to 
the left and you'll wonder "wait, how **exactly** has this EVER worked for me all this time?".
Which can be followed by other doubts: "if I don't understand this, what else don't I know?",
which can lead to "do I really understand ANYTHING?".  And, before you know it, you'll be
over at your parent's house demanding to know if you were adopted.

Best to nip these slippery slopes in the bud (and, equally, to never use mixed metaphors).  Read on ...

## Components

In an earlier tutorial [Creating Reagent Components](CreatingReagentComponents.md), we saw that the centerpiece of a Reagent/React component is a **renderer** function. Optionally, a Component might have other lifecycle functions, but a renderer function is central and mandatory.

We also saw that Reagent render functions turn data into hiccup:   `data -> hiccup`

## Meet greet

Here's an example Component:
```clj
(defn greet
  [name]
  [:div  "Hello " name])
```

No, wait, what? That's not a Component, that's a function.

Yes, indeed it is. And, right there, we have the nub of the issue. 

It is only when `greet` is **used in a particular way**, that it is "promoted" to become the renderer for a Reagent Component. Unless you are paying attention, you might not even realise when that magic is happening, and not happening. 

Right. So given **use is king**, let's look at two forms of it ...

## Using Greet Via ()

If you **use** `greet` in a function call via `()`, it returns a vector with 3 elements: 
```cljs
(greet "You")
;; => [:div  "Hello " "You"]   ;; a vector of a keyword and two strings

(first (greet "You"))
;; => :div

(second (greet "You"))
;; => "Hello"
```

So, simply calling such function certainly doesn't magically create a Reagent Component. It just returns a vector.

Hmmm. How about we call `greet` within another function: 
```clj
(defn greet-family-round       ;; round on the end because using round brackets
  [member1 member2 member3]
  [:div
    (greet member1)    ;; return value put into the vector
    (greet member2)    ;; and again 
    (greet member3)])  ;; and again
```

`greet-family-round` returns a 4 element vector. And what's in that vector if we call it like this?
```cljs
(greet-family-round "Mum" "Dad" "Aunt Edith")
```

Because `(greet "Mum")` returns `[:div  "Hello " "Mum"]`, that's the 2nd element in the vector. And so on.
```cljs
 [:div
    [:div  "Hello " "Mum"]          ;; <-- (greet "Mum") 
    [:div  "Hello " "Dad"]          ;; <-- (greet "Dad") 
    [:div  "Hello " "Aunt Edith"]]  ;; <-- (greet "Aunt Edith") 
```

## Using Greet Via []

Let's keep `greet` the same, but change the way it is **used**: 
```clj
(defn greet-family-square
  [member1 member2 member3]
  [:div
    [greet member1]      ;; not using ()
    [greet member2]     
    [greet member3]])  
```

Let's be crystal clear:  `[greet member1]` is a two element vector, just like `[1 2]`. 

And, what would this call return?
```cljs
(greet-family-square "Mum" "Dad" "Aunt Edith")  
```

Answer:
```cljs
 [:div
    [greet "Mum"]         
    [greet "Dad"]
    [greet "Aunt Edith"]]
```

So, `greet` is not called inside of `greet-family-square`. Instead, it is placed into a vector. 

## The Difference Between () and []

Here is the hiccup returned by `greet-family-round`.
```cljs
 [:div
    [:div  "Hello " "Mum"]          ;; the return value of greet put in here
    [:div  "Hello " "Dad"]          ;; and again 
    [:div  "Hello " "Aunt Edith"]]  ;; and again
```
You'll notice this hiccup contains no references to `greet`. Only the (hiccup) values returned by calls to `greet`.

On the other hand, there **are** references to `greet` in the hiccup returned by `greet-family-square`
```cljs
 [:div
    [greet "Mum"]         
    [greet "Dad"]
    [greet "Aunt Edith"]]
```

## The Interpretation Of Hiccup

After renderers return hiccup, Reagent interprets it. 

As it does this interpretation, if Reagent sees a vector where the first element is a function, for example `[greet "Mum"]`, it interprets that function as a renderer **and it builds a React component around that renderer**. 

Let's pause and remember that a renderer function is the key, mandatory, central part of a Component. Defaults can be supplied for the other React lifecycle functions, like `component-should-update`, but a renderer **must** be supplied.

So Reagent recognises `greet` as a candidate renderer function and, if it is found in the right place (1st element of a vector), Reagent will mix it with other default lifecycle functions to form a full React/Reagent Component. It gives `greet` a, er, promotion.

The other elements of the vector, after `greet`, are interpreted as parameters to the renderer - in React terms, `props`. 

## Which and Why?

So, which variation of `greet-family` (`square` vs `round`) should I choose, and why?

The answer to "which?" is easy: you almost certainly want the `square` version.  "why?" takes more explanation ... 

First off, let's acknowledge that both variations will ultimately produce the same DOM, so in that respect they are the same. 

Despite this identical outcome, they differ in one significant way: 

  1. the `square` version will create each `greet` child as a distinct React component, each with its own React lifecycle, **allowing them to re-render independently of siblings**. 
  2. The `round` version causes the `greet` hiccup for all children to be incorporated  into the hiccup returned by the parent, forming one large data structure, parent and children all in together. So, each time the parent re-renders, all the `greet` children are effectively re-rendered too. React must then work out what, in this tree, has changed.

As a result, **the `square` version will be more efficient at "re-render time"**.  Only the DOM which needs to be re-rendered will be done. At our toy scale in this tutorial it hardly matters but, if `greet` was a more substantial child component, this gain in efficiency could be significant.

Armed with a bit more knowledge, we'll revisit this subject at the end of the next Tutorial.

## A Further, Significant "Why"

In the examples above, we've explored [Form-1 components](CreatingReagentComponents.md#form-1-a-simple-function)  - the simplest kind - and we've seen we have some choice regarding use of `()` or `[]`. Eventually, I claim that `[]` is much preferred, but you can get away with `()`, up to a point. 

**But** ... the moment you start using Form-2 or Form-3 components, you absolutely must be using `[]`.  No choice.  Using `()` just won't work at all. Given the explanations above, I'm hoping you can work out why. Either that or just shrug and use `[]` forever more. 

### Appendix #1

`hiccup` can be created like any normal cljs data structure. You don't have to use literals.

Our version of `greet-family-round` from above returns something of a 4 element vector literal:
```clj
(defn greet-family-round
  [member1 member2 member3]
  [:div
    (greet member1)    
    (greet member2)  
    (greet member3)]) 
```

Here's a rewrite in which the hiccup is less literal and more generated: 
```clj
(defn greet-family-round-2       ;; a re-write 
  [& members]
  (into [:div] (map greet members)))
```

When called with 3 parameters, both versions of this function return the same hiccup:
```clj
(= (greet-family-round   "Mum" "Dad" "Aunt Edith") 
   (greet-family-round-2 "Mum" "Dad" "Aunt Edith"))
;; => true
```

### Appendix #2

When interpreting hiccup, Reagent regards vectors as special, and it has some demands about their 1st element.

In Reagent hiccup, the 1st element of a vector **must always** be something it can use to build a Component. 

Reagent can use `greet` to build a Component, so that works.  So does `:div` because Reagent knows what Component you mean. And there are a few other options.

So this is okay:   `[greet ...]`  and so is this  `[:div ...]`

**But** if your hiccup contains a vector like `[1 2 3]`, then you'll get an error because Reagent can't use `1` to build a Component. 

So this code has a problem:
```clj 
(defn greet-v
   [v]
   (into [:div] (map greet v)))

(defn greet-family
   []
   [greet-v ["Mum" "Dad" "Aunt Edith"]])  ;; <-- error here
```

Notice the vector `["Mum" "Dad" "Aunt Edith"]` in the hiccup.  Reagent will try to build a Component using "Mum" (1st element in a vector) and, when that doesn't work, it will report an error.  

## Next Step

We've now seen how we can use functions and `[]` to create Components. In the [next tutorial](WhenDoComponentsUpdate.md), we'll understand how and when these Components update. 
