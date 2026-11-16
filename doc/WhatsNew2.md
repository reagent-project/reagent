# What's new in Reagent 2.0

Reagent 2.0 (2025-10-29) adds React 19 support, a new way to define function
components and a hooks namespace. To upgrade an existing app, see the
[upgrade guide](2.0-upgrade.md). All changes are listed in the
[changelog](https://github.com/reagent-project/reagent/blob/master/CHANGELOG.md).

## React 19 and `reagent.dom.client`

Reagent 2.0 supports React 19 through the `reagent.dom.client` namespace, which
uses React's `react-dom/client` API (and so React's concurrent rendering):

```cljs
(ns example.core
  (:require [reagent.dom.client :as rdomc]))

(defn view []
  [:div "Hello world"])

(defonce root (rdomc/create-root (.getElementById js/document "app")))

(defn ^:export run []
  (rdomc/render root [view]))
```

`reagent.dom.client` also has `hydrate-root` for server-rendered HTML, and
`unmount`.

The old `reagent.dom/render` still works with React 17 and 18, but not with
React 19. Reagent is tested against both React 18 (with `reagent.dom`) and
React 19 (with `reagent.dom.client`).

Re-renders caused by ratom and reaction changes are now triggered synchronously
with `react-dom/flushSync`, so an update no longer waits an extra animation
frame before it reaches the DOM. See [React 19 and Reagent](React19.md) for notes on batching.

## `defc`: function components without `:f>`

`reagent.core/defc` defines a component that is a React function component, so
it can use React hooks. It is used in Hiccup like any other Reagent component,
without the `:f>` shortcut:

```cljs
(ns example.core
  (:require [reagent.core :as r]
            [reagent.hooks :as hooks]))

(r/defc counter [{:keys [label]}]
  (let [[n set-n] (hooks/use-state 0)]
    [:button {:on-click #(set-n (inc n))}
     label ": " n]))

(defn app []
  [counter {:label "Clicks"}])
```

Ratoms work in `defc` components the same way as in other Reagent components.
Unlike a component defined with `defn`, a `defc` component can't be called as
a regular function.

## `reagent.hooks`

The new `reagent.hooks` namespace wraps the React hooks (`use-state`,
`use-effect`, `use-memo`, `use-callback`, `use-ref`, `use-reducer` and others)
so that they work with Clojure values:

- Dependencies are compared with Clojure equality, so maps and vectors can be
  used in dependency vectors.
- `use-state` and `use-reducer` keep the previous state when the new state is
  equal to it, so an equal value doesn't cause a re-render.
- Effect callbacks can return any value; only a function return value is used
  as the cleanup function.

See the [`reagent.hooks` API docs](https://cljdoc.org/d/reagent/reagent/CURRENT/api/reagent.hooks).

## StrictMode fixes

Ratoms no longer stop updating components rendered inside React StrictMode,
both in function components (2.0.0) and in class components (2.0.1).

## Removed

- `reagent.dom/dom-node`: React 19 removed the underlying `findDOMNode`. Use
  [refs](FAQ/UsingRefs.md) instead.
- The second (`deep`) argument of `reagent.core/force-update`.
- Cljsjs: React 19 is no longer published as a UMD module, so there are no
  Cljsjs packages for it. Use React from npm.
