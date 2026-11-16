# Best Practices

> **Draft.** This page is a placeholder. The guidance will be written by the
> Reagent maintainers; the list below is only an outline of candidate topics,
> collected from the existing guides.

## Candidate topics

- **Rendering entry point**: `reagent.dom.client/create-root` and `render`
  for React 18 and 19, instead of the legacy `reagent.dom/render`
  ([React 19 and Reagent](React19.md), [Upgrading to 2.0](2.0-upgrade.md)).
- **Choosing a component style**: form-1/2/3 class components vs. function
  components and hooks, and when to use `:f>` or the function component
  compiler ([Creating Reagent Components](CreatingReagentComponents.md),
  [Reagent Compiler](ReagentCompiler.md), [React Features](ReactFeatures.md)).
- **Calling components**: `[component args]` vs. `(component args)`, and why
  `defc` components can only be used in Hiccup
  ([Square brackets vs parentheses](UsingSquareBracketsInsteadOfParens.md),
  [What's new in 2.0](WhatsNew2.md)).
- **Where state lives**: app state in a few ratoms vs. local state in form-2
  components or `with-let`; cursors, reactions and `track`
  ([Managing State](ManagingState.md)).
- **Dereferencing**: deref inside render, not inside lazy sequences
  (or wrap them in `doall`)
  ([Component not re-rendering](FAQ/ComponentNotRerendering.md),
  [Managing State](ManagingState.md)).
- **Props and re-rendering**: passing values vs. atoms, equality checks, and
  keeping props stable ([When do components update?](WhenDoComponentsUpdate.md)).
- **Lists and keys**: `^{:key ...}` metadata vs. `:key` prop, and keys to
  force re-creation
  ([Forcing re-creation](FAQ/ForcingComponentRecreation.md)).
- **Inputs**: controlled inputs and cursor-jumping workarounds
  ([Controlled Inputs](ControlledInputs.md)).
- **Timing**: batching, `r/flush`, `next-tick` and `after-render`
  ([Batching and Timing](BatchingAndTiming.md)).
- **React interop**: `:>`, `adapt-react-class`, `as-element`,
  `reactify-component`, and refs
  ([Interop with React](InteropWithReact.md), [Using refs](FAQ/UsingRefs.md)).
- **Security**: untrusted data in `href`, `dangerouslySetInnerHTML` and
  `unsafe-html` ([Security](Security.md)).
