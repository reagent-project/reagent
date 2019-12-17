# Interop with React

A little understanding of what Reagent is doing really helps when trying to use
React libraries and reagent together.

## Creating React Elements directly

The `reagent.core/create-element` function simply calls React's `createElement`
function (and therefore, it expects either a string representing an HTML
element or a React Component).

As an example, here are four ways to create the same element:

```clojure
(defn integration []
  [:div
   [:div.foo "Hello " [:strong "world"]]

   (r/create-element "div"
                     #js{:className "foo"}
                     "Hello "
                     (r/create-element "strong"
                                        #js{}
                                        "world"))

   (r/create-element "div"
                     #js{:className "foo"}
                     "Hello "
                     (r/as-element [:strong "world"]))

   [:div.foo "Hello " (r/create-element "strong"
                                        #js{}
                                        "world")]])

(defn mount-root []
  (rdom/render [integration]
    (.getElementById js/document "app")))
```

This works because `reagent.dom/render` itself expects (1) a React element or (2) a
Hiccup form. If passed an element, it just uses it. If passed a Hiccup, it
creats a (cached) React component and then creates an element from that
component.

## Creating React Elements from Hiccup forms

The `reagent.core/as-element` function creates a React element from a Hiccup
form. In the previous section, we discussed how `reagent.dom/render` expects either
(1) a Hiccup form or (2) a React Element. If it encounters a Hiccup form, it
calls `as-element` on it. When you have a React component that wraps children,
you can pass Hiccup forms to it wrapped in `as-element`.

## Creating Reagent "Components" from React Components

The function `reagent/adapt-react-class` will turn a React Component into
something that can be placed into the first position of a Hiccup form, as if it
were a Reagent function. Take, for example the react-flip-move library and
assume that it has been properly imported as a React Component called
`FlipMove`. By wrapping FlipMove with `adapt-react-class`, we can use it in a
Hiccup form:

```clojure
(defn top-articles [articles]
  [(reagent/adapt-react-class FlipMove)
   {:duration 750
    :easing "ease-out"}
   articles]
```

There is also a convenience mechanism `:>` (colon greater-than) that shortens
this and avoid some parenthesis:

```clojure
(defn top-articles [articles]
  [:> FlipMove
   {:duration 750
    :easing "ease-out"}
   articles]
```

This is the equivalent JavaScript:

```clojure
const TopArticles = ({ articles }) => (
  <FlipMove duration={750} easing="ease-out">
    {articles}
  </FlipMove>
);
```

## Creating React Components from Reagent "Components"

The `reagent/reactify-component` will take a Form-1, Form-2, or Form-3 reagent "component". For example:

```clojure
(defn exported [props]
  [:div "Hi, " (:name props)])

(def react-comp (r/reactify-component exported))

(defn could-be-jsx []
  (r/create-element react-comp #js{:name "world"}))
```

Note:

* `adapt-react-class` and `reactify-component` are not perfectly symmetrical,
because `reactify-component` requires that the reagent component accept
everything in a single props map, including its children.

## Example: "Decorator" Higher-Order Components

Some React libraries use the decorator pattern: a React component which takes a
component as an argument and returns a new component as its result. One example
is the React DnD library. We will need to use both `adapt-react-class` and
`reactify-component` to move back and forth between React and reagent:

```clojure
(def react-dnd-component
  (let [decorator (DragDropContext HTML5Backend)]
    (reagent/adapt-react-class
      (decorator (reagent/reactify-component top-level-component)))))
```

This is the equivalent JavaScript:

```clojure
import HTML5Backend from 'react-dnd-html5-backend';
import { DragDropContext } from 'react-dnd';

class TopLevelComponent {
  /* ... */
}

export default DragDropContext(HTML5Backend)(TopLevelComponent);
```

## Example: Function-as-child Components

Some React components expect a function as their only child. React AutoSizer is one such example.

```clojure
[(reagent/adapt-react-class AutoSizer)
 {}
 (fn [dims]
  (let [dims (js->clj dims :keywordize-keys true)]
   (reagent/as-element [my-component (:height dims)])))]
```

## Getting props and children of current component

Because you just pass arguments to reagent functions, you typically don't need
to think about "props" and "children" as distinct things. But Reagent does make
a distinction and it is helpful to understand this, particularly when
interoperating with native elements and React libraries.

Specifically, if the first argument to your Reagent function is a map, that is
assigned to `this.props` of the underlying Reagent component. All other
arguments are assigned as children to `this.props.children`.

When interacting with native React components, it may be helpful to access
props and children, which you can do with `reagent.core/current-component`.
This function returns an object that allows you retrieve the props and children
passed to the current component.

Beware that `current-component` is only valid in component functions, and must
be called outside of e.g. event handlers and `for` expressions, so it's safest
to always put the call at the top, as in `my-div` here:

```clojure
(ns example
  (:require [reagent.core :as r]))

(defn my-div []
  (let [this (r/current-component)]
    (into [:div.custom (r/props this)]
          (r/children this))))

(defn call-my-div []
  [:div
    [my-div "Some text."]
    [my-div {:style {:font-weight 'bold}}
      [:p "Some other text in bold."]]])
```

## React Features

- [React Features and how to use them in Reagent](./ReactFeatures.md)

## Examples

- [Material-UI](../examples/material-ui/src/example/core.cljs)
- [React-sortable-hoc](../examples/react-sortable-hoc/src/example/core.cljs)
