# Material-UI

[Example project](../../examples/material-ui/)

Material-UI [TextField](https://material-ui.com/api/text-field/) has for long
time caused problems for Reagent users. The problem is that `TextField` wraps the
`input` element inside a component so that Reagent is not able to enable
input cursor fixes, which are required due to [async rendering](http://reagent-project.github.io/news/reagent-is-async.html).

Note that this only happens when using [controlled inputs](https://reactjs.org/docs/forms.html#controlled-components). In some cases you can workaround the problem by using [an uncontrolled input](https://reactjs.org/docs/uncontrolled-components.html) (i.e. `:default-value`). See also [Reagent controlled inputs](../ControlledInputs.md).

Good news is that Material-UI v1 has a property that can be used to provide
the input component to `TextField`:

```cljs
(ns example.material-ui
  (:require ["material-ui" :as mui]
            [reagent.core :as r]))

(def text-field (r/adapt-react-class mui/TextField))

(def value (r/atom ""))

(def input-component
  (r/reactify-component
    (fn [props]
      [:input (-> props
                  (assoc :ref (:inputRef props))
                  (dissoc :inputRef))])))

(def example []
  [text-field
   {:value @value
    :on-change #(reset! value (.. e -target -value))
    :InputProps {:inputComponent input-component}}])
```

`reactify-component` can be used to convert Reagent component into React component,
which can then be passed into Material-UI. The component should be created once
(i.e. on top level) to ensure it is not unnecessarily redefined, causing the
component to be re-mounted.
For some reason Material-UI uses different name for `ref`, so the `inputRef` property
should be renamed by the input component.

## Wrapping for easy use

Instead of providing `:InputProps :inputComponent` option to every `TextField`,
it is useful to wrap the `TextField` component in a way that the option is added always:

```cljs
(defn text-field [props & children]
  (let [props (-> props
                  (assoc-in [:InputProps :inputComponent] input-component)
                  rtpl/convert-prop-value)]
    (apply r/create-element mui/TextField props (map r/as-element children))))
```

Here `r/create-element` and `reagent.impl.template/convert-prop-value` achieve
the same as what `adapt-react-class` does, but allows modifying the props.

**Check the example project for complete code.** Some additional logic is
required to ensure option like `:multiline` and `:select` work correctly,
as they affect how the `inputComponent` should work.

TODO: `:multiline` `TextField` without `:rows` (i.e. automatic height) doesn't
work, because that requires Material-UI `Input/Textarea`, which doesn't work
with Reagent cursor fix.
