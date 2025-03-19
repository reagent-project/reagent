# React Renderers

React itself is not limited to HTML and the DOM.
[ReactDOM](https://react.dev/reference/react-dom) is one implementation that
renders React elements into the DOM and HTML.

Reagent does not have test cases for other renderers, but there should be
nothing in its implementation that is specifically tied to the DOM or HTML,
meaning it should work with other rendering targets.

Reagent only requires the `react-dom` module in the `reagent.dom`,
`reagent.dom.client`, and `reagent.dom.server` namespaces. This means you can
use `reagent.core` with other targets without unintentionally requiring
`react-dom`.

## Other Rendering Targets

One of the most common alternative targets is [React
Native](https://reactnative.dev/). Reagent has been successfully used with
React Native applications for years.

Reagent does not provide built-in `render` functions for non-DOM targets, so
you will need to handle rendering manually. This is typically done using
`reagent.core/as-element` to convert a Reagent Hiccup form into a React element
for use with a renderer, or `reagent.core/reactify-component` to turn a
function into a React component.

Example:

```clj
(ns app.main
  (:require ["react-native" :refer [View Text AppRegistry]]))

(defn root []
  [:> View [:> Text "Hello world"]])

;; Using reactify-component to create a React component
(def Root (r/reactify-component root))

;; Using as-element, where the function itself returns a React element
(defn Root []
  (r/as-element [root]))

;; React Native entry point
(defn init []
  (.registerComponent AppRegistry "App" (fn [] Root)))
```

### Other Examples of Alternative Render Targets

- [react-three-fiber](https://r3f.docs.pmnd.rs/getting-started/introduction) (for 3D rendering)
- [Ink](https://github.com/vadimdemedes/ink) (for rendering React components in the terminal)

## Reporting Issues

If you encounter issues with other rendering targets, consider opening an
issue. Providing a minimal example repository that reproduces the problem will
be extremely helpful, as the Reagent project does not include examples for all
possible render targets.
