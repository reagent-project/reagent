### Question

My component is not rerendering, what should I do?

### Answer

This could happen for a few reasons. Try the following:

1. Make sure you are using a `reagent.core/atom` (i.e., ratom) instead of a normal `atom`.
2. Make sure to deref your ratom (e.g, @app-state) inside of your component.
3. Make sure your ratom will survive a rerender. Either declare it as a global var, or use a form-2 component. [Read this](https://github.com/reagent-project/reagent-cookbook/tree/master/basics/component-level-state) if you want to understand why.
4. Make sure to deref your ratom outside of a seq or wrap that seq in a `doall`. See this [related issue](https://github.com/reagent-project/reagent/issues/18).

***

Up:  [FAQ Index](README.md)&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
