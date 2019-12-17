# Using Hiccup to Describe HTML

Reagent uses a datastructure known as Hiccup to describe HTML. Hiccup describes HTML elements and user-defined components as a nested ClojureScript vector:

```clojure
[:div {:class "parent"}
  [:p {:id "child-one"} "I'm first child element."]
  [:p "I'm the second child element."]]
```

As described below, reagent provides a number of extensions and conveniences to Hiccup, but the general rules of Hiccup are as follows:

1. The first element is either a keyword or a symbol
   * If it is a keyword, the element is an HTML element where `(name keyword)` is the tag of the HTML element.
   * If it is a symbol, reagent will treat the vector as a component, as described in the next section.
2. If the second element is a map, it represents the attributes to the element. The attribute map may be omitted.
3. Any additional elements must either be Hiccup vectors representing child nodes or string literals representing child text nodes.

## Special treatment of `nil` child nodes

Reagent and React ignore nil nodes, which allow conditional logic in Hiccup forms:

```clojure
(defn my-div [child?]
  [:div
    "Parent Element"
    (when child? [:div "Child element"])])
```

In this example `(my-div false)` will evaluate to `[:div "Parent Element" nil]`, which reagent will simply treat the same as `[:div "Parent Element"]`.

## Special interpretation of `style` attribute

The `:style` attribute can be written a string or as a map. The following two are equivalent:

```clojure
[:div {:style "color: red; font-weight: bold"} "Alert"]
[:div {:style {:color "red"
               :font-weight "bold"}
      "Alert"]
```

The map form is the same as [React's style attribute](https://reactjs.org/docs/dom-elements.html#style), except that when using the map form of the style attribute, the keys should be the same as the CSS attribute as shown in the example above (not in camel case as is required JavaScript).

## Special interpretation of `class` attribute

In JavaScript, `class` is a reserved keyword, so React uses the `className` to specify class attibutes. Reagent just uses `class`.

As of reagent 0.8.0, the `class` attribute accepts a collection of classes and will remove any nil value:

```clojure
[:div {:class ["a-class" (when active? "active") "b-class"]}]
```

## Special notation for id and class

The id of an element can be indicated with a hash (`#`) after the name of the element.

This:

```clojure
[:div#my-id]
```

is the same as this:

```clojure
[:div {:id "my-id"}]
```

One or more classes can be indicated for an element with a `.` and the class-name like this:

```clojure
[:div.my-class.my-other-class.etc]
```

which is the same as:

```clojure
[:div {:class ["my-class" "my-other-class" "etc"]}]
```

Special notations for id and classes can be used together. The id must be listed first:

```clojure
[:div#my-id.my-class.my-other-class]
```

which is the same as:

```clojure
[:div {:id "my-id" :class ["my-class" "my-other-class"]}]
```

## Special notation for nested elements

Reagent extends standard Hiccup in one way: it is possible to stack elements together by using a `>` character.

This:

```clojure
[:div
  [:p
    [:b "Nested Element"]]]
```

can be written as:

```clojure
[:div>p>b "Nested Element"]
```

## Rendering Hiccup

The primary entrypoint to the reagent library is `reagent.core/render`.

```clojure
(ns example
  (:require [reagent.core :as r]))

(defn render-simple []
  (rdom/render [:div [:p "Hello world!"]]
    (.-body js/document)))
```

This `render` function expects one of two things:

1. A React Element, which will just be passed to React as is.
2. A ClojureScript vector (i.e., a Hiccup form).

If it encounters a ClojureScript vector, it will interpret it as Hiccup. Reagent expects one of two things in the first position of the vector:

1. A keyword like `:div` or `:span`, which it will create using React.createElement
2. A symbol like `my-component`.

If it's a symbol, then reagent will evaluate a function by that name. Reagent expects one of three things from this function:

1. A Hiccup vector. Reagent creates a React component with the function as its render method and uses the Hiccup vector for the initial render.
2. A ClojureScript function. Reagent will then create a React component with this inner function as the render method and will then call the inner function for the initial render.
3. A React component. Reagent will render this using React.createElement. Note, this could be a result of calling `reagent.core/create-class` or it could be a React component you have imported from a JavaScript library.

