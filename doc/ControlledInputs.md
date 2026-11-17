# Controlled inputs

Reagent uses async rendering which causes problems with controlled inputs. If
the input element is created directly by Reagent (i.e. `[:input ...]` in hiccup), [a
workaround](https://github.com/reagent-project/reagent/blob/master/src/reagent/impl/input.cljs)
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

## Async state updates

`:on-change` can update the state on a later tick, e.g. with re-frame
`dispatch`. While the state lags behind the typed text, Reagent doesn't write
the older values back to the input, also when something else re-renders the
input meanwhile (e.g. its parent). Reagent only writes the value to the input
when the input renders a new value that `:on-change` didn't report, e.g. when
the application clears the input.

## Rejecting a change

If `:on-change` keeps the state unchanged, the typed text stays in the input.
Reagent can't tell this apart from state that is updated later (e.g. a re-frame
event queue that is busy for longer than a frame), and writing the rendered
value back then would lose the keys typed meanwhile. To reject a change,
`:on-change` can put the current value back into the input itself:

```clojure
[:input {:value @state
         :on-change (fn [e]
                      (let [v (.. e -target -value)]
                        (if (re-matches #"\d*" v)
                          (reset! state v)
                          (set! (.. e -target -value) @state))))}]
```

Reagent keeps the cursor where the key was typed (its distance from the end
of the text), unless `:on-change` moves it itself. With re-frame, validate in
`:on-change` before dispatching, as the event handler doesn't see the input.

## Known issues

- If `:on-change` transforms the value (e.g. upper-cases it) and updates the
  state asynchronously, fast typing can lose keys: Reagent can't tell the
  transformed value apart from a new value set by the application. Update the
  state synchronously in this case, e.g. with re-frame `dispatch-sync`.
- If `:on-change` rejects a change by keeping the state unchanged, the typed
  text stays in the input, but not in the state. Put the value back into the
  input in `:on-change`, see [Rejecting a change](#rejecting-a-change).
- If `:on-change` transforms the typed text back to the current state (e.g.
  drops a typed `0`), nothing is rendered and the typed text stays, as with a
  rejected change. Put the value back into the input as shown above.
- `email` and `number` inputs don't have the Selection API, so Reagent can't
  restore the cursor when it writes a different value to them. If
  `:on-change` changes the typed value of such an input, the cursor moves to
  the end. Typing with the value stored as-is works.
- When `:on-change` changes the value of a text input that is too narrow for
  its text, Chrome can leave the cursor and the last character just outside
  the visible area after Reagent writes the value
  ([#595](https://github.com/reagent-project/reagent/issues/595)).

## Trying it out

The Reagent repository has a test page with a controlled input for each of
these cases, with notes on what to try and which known issues to expect
([source](https://github.com/reagent-project/reagent/blob/master/test/reagenttest/e2e/inputs.cljs)).
The same page is used by browser tests that type into it with real keyboard
and IME input
([e2e/inputs.spec.js](https://github.com/reagent-project/reagent/blob/master/e2e/inputs.spec.js)).
To open it, in a checkout of the repository (needs Node and Java):

```bash
npm ci
npm run e2e:page
```

Then open http://127.0.0.1:8091.

## JS libraries

If a JS component (e.g. a text field from a component library) renders the
input element, Reagent only sees the component and the workaround isn't
applied. Such a controlled input behaves badly even when `:on-change` updates
a ratom synchronously: the cursor jumps to the end when typing in the middle,
fast typing loses keys, and IME input (e.g. Korean or Chinese) doubles letters.
The test page under [Trying it out](#trying-it-out) shows this, next to the
same component with Reagent's input passed to it.

For JS libraries, usually the best solution is if the library provides an option to
use custom component to create the input element, which enables
Reagent to create the input element:

- [Material UI](./examples/material-ui.md)
- [Smooth UI](./examples/smooth-ui.md)

## React-native

ReactNative has its own `TextInput` component. Similar workaround can't be (at least easily) implemented in ReactNative, as the component doesn't provide similar API as DOM Inputs to control the selection. Currently best option is to use uncontrolled inputs (`default-value` and `on-change`). If you also need to update the input value from your code, you could change to Input component React key to force recreation of the component:

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
