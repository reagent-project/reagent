# Reagent Compiler

Reagent Compiler object is a new way to configure how Reagent
turns the Hiccup-style markup into React components and elements.

As a first step, this can be used to turn on option to create
functional components when a function is referred in a Hiccup vector:
`[component-fn parameters]`.

[./ReactFeatures.md#hooks](React more about Hooks)

```cljs
(def functional-compiler (r/create-compiler {:functional-components? true}))

;; Using the option
(r/render [main] div functional-compiler)
(r/as-element [main] functional-compiler)
;; Setting compiler as the default
(r/set-default-compiler! functional-compiler)
```

## Reasoning

Now that this mechanism to control how Reagent compiles Hiccup-style markup
to React calls is in place, it will be probably used later to control
some other things also:

From [Clojurist Together announcenment](https://www.clojuriststogether.org/news/q1-2020-funding-announcement/):

> As this [hooks] affects how Reagent turns Hiccup to React elements and components, I
> have some ideas on allowing users configure the Reagent Hiccup compiler,
> similar to what [Hicada](https://github.com/rauhs/hicada) does. This would also allow introducing optional
> features which would break existing Reagent code, by making users opt-in to
> these. One case would be to make React component interop simpler.

Some ideas:

- Providing options to control how component parameters are converted to JS
objects (or even disable automatic conversion)
- Implement support for custom tags (if you can provide your own function
to create element from a keyword, this will be easy)

Open questions:

- Will this cause problems for libraries? Do the libraries have to start
calling `as-element` with their own Compiler to ensure compatibility.
