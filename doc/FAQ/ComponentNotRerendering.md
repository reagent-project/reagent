### Question

My component is not re-rendering, what's wrong?

### Answer

Ask yourself this question: why do you think the Component should have re-rendered? There's two possible answers: 
  - a ratom (used by the Component) changed 
  - the props for (arguments to) the Component changed

We'll deal with these two cases seperately.

### A Ratom Changed

If a ratom changes but your Component doesn't update, then the gotchas to look out for are: 
1. Make sure you are using a `reagent.core/atom` (i.e. a Reagent ratom) instead of a normal `clojure.core/atom`. Carefully check the `require` at the top of the `ns`.  Components are only reactive with respect to Reagent ratoms. They don't react to changes in a Clojure atom. 
2. Make sure you actually `deref` your ratom (e.g. `@app-state`) during the render function of your component. It is a common mistake for people to forget the leading `@`.  Note that derefs that happen outside of the render function (such as during event handlers) do not make your component reactive to that ratom.
3. Make sure your ratom will survive a rerender. Either declare it as a global var, or use a form-2 or form-3 component. [Read this](https://github.com/reagent-project/reagent-cookbook/tree/master/basics/component-level-state) if you want to understand why.
4. If you put your ratom in a form-2 or form-3 component, be sure you are calling that function using `[square brackets]`, not `(parenthesis)`.
When function (component) is called using `( )` Reagent doesn't create new component, but instead just places the function's return value into current component. In this case the function closure which should hold the local state doesn't work.
5. Make sure to `deref` your ratom outside of a seq or wrap that seq in a `doall`. See this [related issue](https://github.com/reagent-project/reagent/issues/18).

### Props Change

If the props to a Component change, but it doesn't appear to re-render, then the cause will be this rookie mistake: you forgot to repeat the parameters in the inner, anonymous render function.

```clj
(defn outer 
  [a b c]            ;; <--- parameters
  ;;  ....
  (fn [a b c]        ;; <--- forgetting to repeat parameters here is the mistake
    [:div
      (str a b c)]))
```

If you forget, the component renderer will stubbornly only ever render the 
original parameter values, not the updated ones, which can be baffling for 
a beginner.

Remember, `outer` is called once per component instance. The parameters to `outer` 
will hold the initial parameter values. The inner renderer on the other hand, 
will be called by Reagent many times and, each time, potentially with alternative 
parameter values, but unless you repeat the parameters on the renderer it will 
close over those initial values in `outer`. As a result, the component renderer 
will stubbornly only ever render the original parameter values, not the updated ones. 


***

Up:  [FAQ Index](../README.md)&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
