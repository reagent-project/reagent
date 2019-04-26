# Controlled inputs

Reagent uses async rendering which cause problems with controlled inputs. If
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

## Examples

- [Material UI](./examples/material-ui.md)
- [Smooth UI](./examples/smooth-ui.md)
