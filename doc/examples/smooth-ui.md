# Smooth UI

Smooth UI has the same problem with [controlled inputs](../CotrolledInputs.md)
as [Material UI](./material-ui.md).
The problem can be solved by providing custom component to Smooth UI inputs
which will create the Input element using Reagent, enabling Reagent to use
it's workaround logic to control input value and cursor position:

```cljs
(def r-input (r/reactify-component
               (fn [props]
                 ;; Omit:
                 ; https://github.com/smooth-code/smooth-ui/blob/c5f3c75a438a04e766dbedeafc2be54252a5338e/packages/shared/core/createComponent.js#L31
                 ; https://github.com/smooth-code/ smooth-ui/blob/c5f3c75a438a04e766dbedeafc2be54252a5338e/packages/shared/core/Input.js#L84
                 ;; Maybe also:
                 ; (.. system -meta -props)
                 [:input (dissoc props :__scTheme :theme :control :size :valid)])))

(rdom/render [:> Input {:as r-input ...}] container)
```
