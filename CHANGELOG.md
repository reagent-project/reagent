
# Changelog


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
