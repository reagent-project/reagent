# Controlled inputs

Reagent uses async rendering which causes problems with controlled inputs. If
the input element is created directly by Reagent (i.e. `[:input ...]` in hiccup), [a
workaround](https://github.com/reagent-project/reagent/blob/master/src/reagent/impl/template.cljs#L132-L238)
can be applied, but if the input is created by JS library (i.e. JSX `<input>`
or React `create-element`), Reagent doesn't see
the element so the workaround can't be applied.

Due to async rendering, the DOM update doesn't occur during the event handler,
but some time later. In certain cases, like when the cursor is not at the end
of the input, updating the DOM input value causes the cursor to move to the
end of the input. Without async rendering, browsers probably implement logic
to keep the cursor position if the value is updated during event handler.

Reagent workaround works by changing the React input element into
uncontrolled input (i.e. the DOM value is not updated by React). Instead
Reagent will update DOM itself if the Reagent input value property changes.
This enables Reagent to check the cursor position before updating the
value, and if needed, save and restore the cursor position
after updating the value.

For JS libraries, usually the best solution is if the library provides an option to
use custom component to create the input element, which enables
Reagent to create the input element:

## React-native

ReactNative has it's own `TextInput` component. Similar workaround can't be (at least easily) implemented in ReactNative, as the component doesn't provide similar API as DOM Inputs to control the selection. Currently best option is to use uncontrolled inputs (`default-value` and `on-change`). If you also need to update the input value from your code, you could change to Input component React key to force recreation of the component:

```clj
[:> TextInput
 {:key @k
  :default-value @v
  :on-change ...}]
  
 (reset! v "foo")
 (swap! k inc)
;; When key changes, old component is unmounted and new one created, and the new component will use the new default-value
```

(Similar workaround can be also used with DOM inputs)

## Examples

- [Material UI](./examples/material-ui.md)
- [Smooth UI](./examples/smooth-ui.md)
