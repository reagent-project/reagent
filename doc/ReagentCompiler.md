# Reagent Compiler

Reagent Compiler object is a new way to configure how Reagent
turns the Hiccup-style markup into React components and elements.

As a first step, this can be used to turn on option to create
functional components when a function is referred in a Hiccup vector:
`[component-fn parameters]`.

<a href="ReactFeatures.md#hooks">Read more about Hooks</a>

```cljs
(def functional-compiler (reagent.core/create-compiler {:function-components true}))

;; Using the option
(reagent.dom/render [main] div functional-compiler)
(reagent.core/as-element [main] functional-compiler)
;; Setting compiler as the default
(reagent.core/set-default-compiler! functional-compiler)
```

## Functional components implementation

Features:

- Ratoms works.
- The functions are wrapped in another function, which uses two
state hooks to store component identity and "update count" - which is used to
force re-render when Ratoms the component uses are updated.
- The functions is wrapped in `react/memo` to implement logic similar to
`shouldComponentUpdate` (component is rendered only if the properties change).
- This implementation passes the same test suite as class components.

Differences to Class component implementation:

- `reagent.dom/render` doesn't return the Component instance, but just `nil`
- `reagent.core/current-component` returns a mocked object that can be passed to `reagent.core/force-update`,
but won't support everything that real Component instance would support.
- A bit slower compared to Class component implementation
- `useEffect` cleanup function is called asynchronously some time after
unmounting the component from DOM (in React 17). This is used to dispose component RAtom,
which will affect e.g. `r/with-let` `finally` function being called. Cleanup
is still called before the component is mounted again. This probably shouldn't
affect any real use cases, but required waiting two animation frames on
Reagent tests to assert that the `finally` was ran.
([More information](https://reactjs.org/blog/2020/08/10/react-v17-rc.html#effect-cleanup-timing))
- Using `r/wrap` as component parameter seems to in some cases re-render
components when source atom is changed, even if the value in path didn't
change. Could be related to how `react/memo` handles changes properties.

![1.0.0-alpha2 benchmark](benchmark.png)

(Local test run with https://github.com/krausest/js-framework-benchmark, with added function component case)

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
