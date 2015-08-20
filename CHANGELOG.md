
# Changelog

## 0.5.1

- React updated to 0.13.3

- Deprecate calling the result of `create-class` as a function (i.e always use hiccup forms to call Reagent components).

- Hiccup syntax has been extended to allow nested elements to be defined using '>' as part of the keyword name.

- Add `force-update` for completeness.

- Try harder to maintain cursor position in inputs.

- Simplify examples, taking advantage of new figwheel.


## 0.5.0

- React updated to 0.12.2

- Reagent no longer bundles React. Instead it uses cljsjs/react as a dependency. This means that you should no longer specify React in `:preamble` in your project.clj.

- ClojureScript 0.0-2816 or later is required.

- `adapt-react-class` makes it easier to use "native" React components with Reagent.

- `reactify-component` makes it easier to use Reagent components in JSX.

- `cursor` is re-written, to be more efficient and flexible.

- `render` now forces a deep update of all components, to make it more convenient to use with e.g. figwheel.

- Renamed `as-component` to `as-element`, to match React's new terminology better (old name still works, though, for backward compatiblity).

- Stop wrapping native components. This reduces the number of components created a lot, and can speed up some things substantially (especially render-to-string, that is not bound by browser performance). This is made possible by a new way of keeping track of which order to re-render dirty components.

- Added `create-element` to make it easier to embed native React 
components in Reagent ones.

- Arguments to components are now compared using simple `=`, instead of the old, rather complicated heuristics. **NOTE**: This means all arguments to a component function must be comparable with `=` (which means that they cannot be for example infinite `seq`s).

- Reagent now creates all React components using `React.createElement` (required for React 0.12).

- `render-component` is now render, and `render-component-to-string` is `render-to-string`, in order to match React 0.12 (but the old names still work).

- Add `render-to-static-markup`. This works exactly like `render-to-string`, except that it doesn't produce `data-react-id` etc.

- `create-class` now takes a Reagent-style render function (i.e with the same arguments you pass to the component), called `:reagent-render`.



## 0.4.3

- React updated to 0.11.2

- Add reagent.core/cursor

- Add javascript interop macros .' and .!

- Add force-update-all to make LightTable integration easier

- Some performance optimizations


## 0.4.2

- Allow multi-methods as component functions.

- Tweak performance by avoiding `clojure.core/memoize`.

- Bugfix: Allow on-change handler on controlled inputs to keep value unchanged.


## 0.4.1

- Made Reagent compatible with ClojureScript 0.0-2173. `reagent.core/atom` now implements the necessary IAtom, ISwap and IReset protocols. Reagent should still be compatible with older ClojureScript versions, but you will get a lot of compilation warnings.


## 0.4.0

- Breaking change: Component functions can get arbitrary arguments, and not just vectors and maps. This is a breaking change, but behaviour is unchanged if you pass a map as the first argument (as in all the examples in the old documentation).

- React updated to 0.9.0.

- You can now use any object that satisfies `ifn?` as a component function, and not just plain functions. That includes functions defined with deftype, defrecord, etc, as well as collections like maps.

- `reagent.core/set-state` and `reagent.core/replace-state` are now implemented using a `reagent.core/atom`, and are consequently async.

- Keys associated with items in a seq (e.g ”dynamic children” in React parlance) can now be specified with meta-data, as well as with a `:key` item in the first parameter as before. In other words, these two forms are now equivalent: `^{:key foo} [:li bar]` and `[:li {:key foo} bar]`.

- Performance has been improved. For example, there is now practically no overhead for tracking derefs in components that don’t use atoms. Allocations and memory use have also been reduced.

- Intro and examples have been tweaked a little to take advantage of the new calling conventions.


## 0.3.0

- Changes in application state are now rendered asynchronously, using requestAnimationFrame.

- Reagent now does proper batching of updates corresponding to changed atoms, i.e parents are rendered before children, and children are only re-rendered once.

- Add `reagent.core/flush` to render changes immediately.

- Bugfix: Allow dynamic id with hiccup-style class names.


## 0.2.1

- Bugfix: allow data-* and aria-* attributes to be passed through unchanged.


## 0.2.0

- Rename Cloact to Reagent, due to popular disgust with the old name...
