# React Features

Most React features should be usable from Reagent, even if Reagent doesn't
provide functions to use them directly.

## [Fragments](https://reactjs.org/docs/fragments.html)

JSX:
```js
function example() {
  return (
    <React.Fragment>
      <ChildA />
      <ChildB />
      <ChildC />
    </React.Fragment>
  );
}
```

Reagent:
```cljs
(defn example []
  [:<>
   [child-a]
   [child-b]
   [child-c]])
```

Reagent syntax follows [React Fragment short syntax](https://reactjs.org/docs/fragments.html#short-syntax).

## [Context](https://reactjs.org/docs/context.html)

```cljs
(defonce my-context (react/createContext "default"))

(def Provider (.-Provider my-context))
(def Consumer (.-Consumer my-context))

(rdom/render
  [:> Provider {:value "bar"}
   [:> Consumer {}
    (fn [v]
      (r/as-element [:div "Context: " v]))]]
  container)
```

[Context example project](../examples/react-context/src/example/core.cljs)
better explains how
`:>` or `adapt-react-class` convert the properties to JS objects,
and shows how to use Cljs values with context.

Alternatively you can use the [static contextType property](https://reactjs.org/docs/context.html#classcontexttype)

```cljs
(defonce my-context (react/createContext "default"))

(def Provider (.-Provider my-context))

(defn show-context []
  (r/create-class
   {:context-type my-context
    :reagent-render (fn []
                      [:p (.-context (reagent.core/current-component))])}))

;; Alternatively with metadata on a form-1 component:
;;
;; (def show-context
;;   ^{:context-type my-context}
;;   (fn []
;;     [:p (.-context (reagent.core/current-component))]))

(rdom/render
  [:> Provider {:value "bar"}
   [show-context]]
  container)
```

Tests contain example of using old React lifecycle Context API (`context-wrapper` function):
[tests](https://github.com/reagent-project/reagent/blob/master/test/reagenttest/testreagent.cljs#L1159-L1168)

## [Error boundaries](https://reactjs.org/docs/error-boundaries.html)

[Relevant method docs](https://reactjs.org/docs/react-component.html#static-getderivedstatefromerror)

You can use `getDerivedStateFromError` (since React 16.6.0 and Reagent 0.9) (and `ComponentDidCatch`) lifecycle method with `create-class`:

```cljs
(defn error-boundary [comp]
  (let [error (r/atom nil)]
    (r/create-class
      {:component-did-catch (fn [this e info])
       :get-derived-state-from-error (fn [e]
                                            (reset! error e)
                                            #js {})
       :reagent-render (fn [comp]
                          (if @error
                            [:div
                             "Something went wrong."
                             [:button {:on-click #(reset! error nil)} "Try again"]]
                            comp))})))
```

Alternatively, one could use React state instead of RAtom to keep track of error state, which
can be more obvious with the new `getDerivedStateFromError` method:

```cljs
(defn error-boundary [comp]
  (r/create-class
    {:constructor (fn [this props]
                    (set! (.-state this) #js {:error nil}))
     :component-did-catch (fn [this e info])
     :get-derived-state-from-error (fn [error] #js {:error error})
     :render (fn [this]
               (r/as-element
                 (if-let [error (.. this -state -error)]
                   [:div
                    "Something went wrong."
                    [:button {:on-click #(.setState this #js {:error nil})} "Try again"]]
                   comp)))}))
```

As per React docs, `getDerivedStateFromError` is what should update the state
after error, it can be also used to update RAtom as in Reagent the Ratom is available
in function closure even for static methods. `ComponentDidCatch` can be used
for side-effects, like logging the error.

## [Function components](https://reactjs.org/docs/components-and-props.html#function-and-class-components)

JavaScript functions are valid React components, but Reagent implementation
by default turns the ClojureScript functions referred in Hiccup-vectors to
Class components.

However, some React features, like Hooks, only work with Functional components.
There are several ways to use functions as components with Reagent:

Calling `r/create-element` directly with a ClojureScript function doesn't
wrap the component in any Reagent wrappers, and will create functional components.
In this case you need to use `r/as-element` inside the function to convert
Hiccup-style markup to elements, or just returns React Elements yourself.
You also can't use Ratoms here, as Ratom implementation requires the component
is wrapped by Reagent.

Using `adapt-react-class` or `:>` is also calls `create-element`, but that
also does automatic conversion of ClojureScript parameters to JS objects,
which isn't usually desired if the component is ClojureScript function.

New way is to configure Reagent Hiccup-compiler to create functional components:
[Read Compiler documentation](./ReagentCompiler.md)

## [Hooks](https://reactjs.org/docs/hooks-intro.html)

NOTE: This section still refers to workaround using Hooks inside
class components, read the previous section to create functional components.

Hooks can't be used inside class components, and Reagent implementation creates
a class component from every function (i.e. Reagent component).

However, you can use React components using Hooks inside Reagent, or use
[hx](https://github.com/Lokeh/hx) components inside Reagent. Also, it is
possible to create React components from Reagent quite easily, because React
function component is just a function that happens to return React elements,
and `r/as-element` does just that:

```cljs
;; This is React function component. Can't use Ratoms here!
(defn example []
  (let [[count set-count] (react/useState 0)]
    (r/as-element
      [:div
       [:p "You clicked " count " times"]
       [:button
        {:on-click #(set-count inc)}
        "Click"]])))

;; Reagent component
(defn reagent-component []
  [:div
   ;; Note :> to use a function as React component
   [:> example]])
```

If you need to pass RAtom state into these components, dereference them in
the Reagent components and pass the value (and if needed, function to update them)
as properties into the React function component.

## [Portals](https://reactjs.org/docs/portals.html)

```cljs
(defn reagent-component []
  (let [el (.. js/document (getElementById "portal-el"))]
    (react-dom/createPortal (r/as-element [:div "foo"]) el)))
```

## [Hydrate](https://reactjs.org/docs/react-dom.html#hydrate)

```cljs
(react-dom/hydrate (r/as-element [main-component]) container)
```

## Component classes

For interop with React libraries, you might need to pass Component classes to other components as parameter. If you have a Reagent component (a function) you can use `r/reactify-component` which returns creates a Class from the function.

If the parent Component awaits classes with some custom methods or properties, you need to be careful and probably should use `r/create-class`. In this case you don't want to use `r/reactify-component` with a function (even if the function returns a class) because `r/reactify-component` wraps the function in another Component class, and parent Component doesn't see the correct class.

```cljs
;; Correct way
(def editor
  (r/create-class
    {:get-input-node (fn [this] ...)
     :reagent-render (fn [] [:input ...])})))

[:> SomeComponent
 {:editor-component editor}]

;; Often incorrect way
(defn editor [parameter]
  (r/create-class
    {:get-input-node (fn [this] ...)
     :reagent-render (fn [] [:input ...])})))

[:> SomeComponent
 {:editor-component (r/reactify-component editor)}]
```

In the latter case, `:editor-component` is a Reagent wrapper class component, which doesn't have the `getInputNode` method and is rendered using the Component created by `create-class` and which has the method.


If you need to add static methods or properties, you need to modify `create-class` return value yourself. The function handles the built-in static-methods (`:childContextTypes :contextTypes :contextType :getDerivedStateFromProps :getDerivedStateFromError`), but not others.

```cljs
(let [klass (r/create-class ...)]
  (set! (.-static-property klass) "foobar")
  (set! (.-static-method klass) (fn [param] ...))
  klass)
```
