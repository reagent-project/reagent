
# Changelog

## Upcoming

- Arguments to components are now compared using simple `=`, instead of the old, rather complicated heuristics. **NOTE**: This means all arguments to a component function must be comparable with `=` (which means that they cannot be for example infinite `seq`s).

- React updated to 0.12.0. Reagent now creates all React components using `React.createElement`.

- `render-component` is now render, and `render-component-to-string` is `render-to-string`, in order to match React 0.12.0 (but the old names still work).

- Add `render-to-static-markup`. This works exactly like `render-to-string`, except that it doesn't produce `data-react-id` etc.


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
