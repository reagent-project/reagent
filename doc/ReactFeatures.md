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
                 (if @error
                   [:div
                    "Something went wrong."
                    [:button {:on-click #(.setState this #js {:error nil})} "Try again"]]
                   (into [:<>] (r/children this)))})))
```

As per React docs, `getDerivedStateFromError` is what should update the state
after error, it can be also used to update RAtom as in Reagent the Ratom is available
in function closure even for static methods. `ComponentDidCatch` can be used
for side-effects, like logging the error.

## [Hooks](https://reactjs.org/docs/hooks-intro.html)

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
  (r/create-class
    {:render (fn [this]
               (let [el (.. js/document (getElementById "portal-el"))]
                 (react-dom/createPortal (r/as-element [:div "foo"]) el)))}))

```

TODO: Can this be done without create-class and `:render`.
TODO: This might have problems handling Ratoms, test.

## [Hydrate](https://reactjs.org/docs/react-dom.html#hydrate)

```cljs
(react-dom/hydrate (r/as-element [main-component]) container)
```
