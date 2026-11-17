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

## Custom compiler

`create-compiler` returns an implementation of the
`reagent.impl.protocols/Compiler` protocol. You can also implement the
protocol yourself, for example to create the React elements with something
else than `react/createElement`.

The `make-element` method is called for HTML elements, inputs, fragments
(`:<>`) and `:>` elements. `jsprops` are the props already converted to a JS
object, and the children are in `argv`, starting from index `first-child`.
The example below implements it with a `create-element` function that takes
the same arguments as `react/createElement`. The other methods do the same as
the compiler from `create-compiler`:

```cljs
(ns example.compiler
  (:require [reagent.impl.component :as comp]
            [reagent.impl.protocols :as p]
            [reagent.impl.template :as tmpl]))

(defn make-element
  "Like `reagent.impl.template/make-element`, but calls `create-element`
  instead of `react/createElement`."
  [create-element compiler argv component jsprops first-child]
  (apply create-element component jsprops
         (map #(p/as-element compiler %) (drop first-child argv))))

(defn create-compiler
  "Like `reagent.core/create-compiler`, but creates the elements with
  `create-element`."
  [opts create-element]
  (let [id (gensym "custom-compiler")
        fn-to-element (if (:function-components opts)
                        tmpl/maybe-function-element
                        tmpl/reag-element)]
    (reify p/Compiler
      (get-id [_this] id)
      (parse-tag [this tag-name tag-value]
        (tmpl/cached-parse this tag-name tag-value))
      (as-element [this x]
        (tmpl/as-element this x fn-to-element))
      (make-element [this argv component jsprops first-child]
        (make-element create-element this argv component jsprops first-child))
      (reactify-component [this c]
        (if (:function-components opts)
          (comp/functional-reactify-component this c)
          (comp/as-class c this))))))
```

For example, [Emotion](https://emotion.sh/docs/css-prop) provides `jsx`, a
replacement for `react/createElement` that supports the `css` prop:

```cljs
(ns example.core
  (:require ["@emotion/react" :as emotion]
            [example.compiler :as compiler]
            [reagent.dom.client :as rdomc]))

(def emotion-compiler (compiler/create-compiler {:function-components true} emotion/jsx))

(defonce root (rdomc/create-root (.getElementById js/document "app")))

(rdomc/render root
              [:div {:css {:color "hotpink"
                           "&:hover" {:color "darkmagenta"}}}
               "Hello"]
              emotion-compiler)
```

Reagent converts the `:css` map to a JS object like the other props, so
nested maps and string keys work. Functions, e.g. Emotion theme functions,
are called with JS arguments and their return value isn't converted: return
a JS object, or wrap the function to convert the result with
`reagent.impl.template/convert-prop-value`.

The `reagent.impl` namespaces are internal: the functions used here can
change between Reagent versions, and the `Compiler` protocol can get new
methods. Check the implementation of `create-compiler` in
`reagent.impl.template` when you update Reagent.

## Reasoning

Now that this mechanism to control how Reagent compiles Hiccup-style markup
to React calls is in place, it will be probably used later to control
some other things also:

From [Clojurist Together announcement](https://www.clojuriststogether.org/news/q1-2020-funding-announcement/):

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
