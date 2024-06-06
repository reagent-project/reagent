# React Refresh

- `reagent.dev` ns must be required before anything that loads `react-dom`
- Don't call `r.dom/render` after reload (e.g. shadow-cljs hook)
- Call `reagent.dev/refresh!` instead
- Only components defined using `r/defc` will refresh
- Reagent doesn't try to create Hook signatures for components,
  so hook state is reset for updated components.
