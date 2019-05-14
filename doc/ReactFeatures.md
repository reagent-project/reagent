# React Features

Most React features should be usable from Reagent, even if Reagent doesn't
provide functions to use them directly.

## Context

```cljs
(defonce my-context (react/createContext "default"))

(def Provider (.-Provider my-context))
(def Consumer (.-Consumer my-context))

(r/render [:> Provider {:value "bar"}
           [:> Consumer {}
            (fn [v]
              (r/as-element [:div "Context: " v]))]]
          container)
```

Tests contain example of using old React lifecycle Context API (`context-wrapper` function):
[tests](https://github.com/reagent-project/reagent/blob/master/test/reagenttest/testreagent.cljs#L1141-L1165)

## Error boundaries

You can use `ComponentDidCatch` lifecycle method with `create-class`:

```cljs
(defn error-boundary [comp]
  (r/create-class
    {:component-did-catch (fn [this e info]
                            (reset! error e))
     :reagent-render (fn [comp]
                        (if @error
                          [:div
                           "Something went wrong."
                           [:button {:on-click #(reset! error nil)} "Try again"]]
                          comp))}))
```

## Hooks

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

## Portals

```cljs
(defn reagent-component []
  (r/create-class
    {:render (fn [this]
               (let [el (.. js/document (getElementById "portal-el"))]
                 (react-dom/createPortal (r/as-element [:div "foo"]) el)))}))

```

TODO: Can this be done without create-class and `:render`.
TODO: This might have problems handling Ratoms, test.

## Hydrate

```cljs
(react-dom/hydrate (r/as-element [main-component]) container)
```
