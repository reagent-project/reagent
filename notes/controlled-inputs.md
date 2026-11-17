# Controlled inputs: rejected changes

Started 2026-11-17. Notes for maintainers, not published docs. Possible future
changes to `src/reagent/impl/input.cljs` after #690 ("Don't restore the
rendered value when on-change keeps it unchanged").

## Where things stand

Since #690, Reagent only writes a value to a controlled input when the input
renders a new value that `:on-change` didn't report. If `:on-change` keeps the
state unchanged (rejects the change, or transforms the typed text back to the
current state), the typed text stays in the input until the next new value.

Why: while the user types, a rejected change looks the same as state that is
updated later (re-frame `dispatch` with a busy event queue). Every earlier
revert check (after a render in 2.0.x, after a `setTimeout` and a render on
master before #690) wrote lagging values back and lost keys, see
[#676](https://github.com/reagent-project/reagent/issues/676) and
[this comment](https://github.com/reagent-project/reagent/issues/676#issuecomment-6951981693).
A check that waits longer only moves the threshold.

What broke: `:on-change` handlers that rely on the revert, e.g.

- rejecting a key by not updating the state (digits-only validation),
- truncating to a maximum length: `(reset! s (subs v 0 5))`,
- filtering: `(reset! s (str/replace v #"\D" ""))`.

The typed text stays visible while the state differs. The documented
workaround (`doc/ControlledInputs.md`, "Rejecting a change") is to write the
last accepted value into the input in `:on-change`. With re-frame that needs
`dispatch-sync`, as the db otherwise lags behind the input.

Whether the breaking change stays is still open. These are options to soften
it or make rejecting easier.

## Possible solutions

### Restore the rendered value on blur

When the input loses focus, write the rendered value if the DOM differs.

- Keys are only lost when the user types into a value written while the state
  lags. On blur nobody types into that input, so no keys are lost.
- If the state was only lagging, the later render has a value that is no
  longer pending, so it is written and the input ends up right. The cost is a
  brief flash of the older value.
- Rejected or truncated text stays visible only while the input has focus.
- Cost: another code path (`:on-blur` wrapping, like `:on-change` in
  `input-render-setup`). The text still differs from the state while typing.

### Opt-in revert for synchronous state

A prop such as `:reagent/revert-rejected true`, or a compiler option, that
brings back the check after `:on-change` (restore the rendered value after a
render if it differs from the DOM) for inputs whose state is always updated
synchronously.

- Apps that relied on the 2.0.x behavior can get it back per input.
- Cost: public API to document and keep. Used with async state, it brings
  the lost keys back.
- Can be combined with the blur restore: blur by default, opt-in for the old
  behavior.

### Give on-change the previous value

Reagent already knows the DOM value before the change (`cljsDOMValue` before
`input-handle-change` updates it), including values the app set. User code
can't track this correctly without copying the pending-value logic: a local
"last accepted value" goes stale when the app sets the value (e.g. clears the
input). Options:

- a function, e.g. `(r/reject-change! e)`, that writes the previous DOM value
  back into the input (and keeps the cursor, which `input-handle-change`
  already does for writes from `:on-change`);
- `:on-change` returning a marker (e.g. `false`) to reject the change.

This makes the documented workaround easy to get right with async state too,
without `dispatch-sync`.

### Cursor when on-change moves it to the end

Not about rejected changes, but found in the same review. After `:on-change`
writes the input, Reagent keeps the cursor's distance from the end unless
`:on-change` moved it. A cursor that `:on-change` put at the end looks the
same as the browser's jump there, so it is moved too (documented, with
`r/after-render` as the workaround).

A prototype fixed this by wrapping the node's `value` property while
`:on-change` runs and fixing the cursor right after each write, so that later
`setSelectionRange` calls in `:on-change` win. It worked (all tests passed)
but wraps React's value tracker property (`trackValueOnNode` in react-dom,
which React defines on the node from the prototype's descriptor), so it
depends on React internals. Not merged; revisit if someone needs it.

## Desired-behavior matrix

For a possible rewrite of the workaround: write the tests for the wanted
behavior first, then build a new implementation (e.g. behind a compiler
option, next to the current one) until they pass. Tests: e2e scenario ids
(`test/reagenttest/e2e/inputs.cljs`, `e2e/inputs.spec.js`) and unit tests
(`test/reagenttest/testinput.cljs`).

Groups:

- **A, browser limit:** no implementation can fix it. Keep as documented
  limits, not as failing tests.
- **B, ambiguous:** Reagent only sees `:on-change` calls and renders, and the
  wanted behavior conflicts with another row (see "Conflicts"). Needs a design
  decision or more information from the app (an API) before it can be a test.
- **C, possible:** the information Reagent has is enough.

Status is as of #690.

| # | Scenario | Wanted | Now | Group | Tests |
|---|----------|--------|-----|-------|-------|
| 1 | Sync state, typing at the end and mid-text (text, textarea) | Keys go at the cursor, Reagent doesn't write | Passes | C | `text-sync`, `textarea-sync`, `sync-on-change-typing` |
| 2 | Sync state, mid-text in email/number, value stored as-is | Caret stays (only because nothing is written) | Passes | C | `email-sync`, `number-sync` |
| 3 | Sync transform (upper-case), mid-text (text, textarea) | Transformed value written, caret after the new text | Passes | C | `upper-sync`, `textarea-upper-sync`, `on-change-transforms-value-unicode` |
| 4 | Sync transform in email/number, mid-text | Caret stays | Caret moves to the end | A (no Selection API) | `email-lower-sync` |
| 5 | Sync transform in a narrow input, typing past the width | Caret and last letter visible | Chrome leaves them outside (#595) | A | `narrow-upper-sync` |
| 6 | IME composition, sync and async state | No doubled or leftover letters | Passes | C | `ime-sync`, `ime-async` |
| 7 | App sets a new value (e.g. a reset button), also while on-change values are pending | Written | Passes | C | `state-changed-from-outside`, `async-on-change-external-change-while-pending` |
| 8 | Async state (a later tick), fast typing, renders between keys and updates | No lost keys, no writes | Passes | C | `text-async`, `async-on-change-*` |
| 9 | Async state, input re-rendered with the lagging value (parent re-render) | Ignored | Passes | C | `text-async-rerender`, `textarea-async-rerender`, `async-on-change-rerender-with-lagging-state` |
| 10 | Async state lagging longer than a frame (busy re-frame queue) | No lost keys | Passes since #690 | B (conflicts with 13-15) | `text-async-slow`, `textarea-async-slow` |
| 11 | Async transform (upper-case), fast typing | Transformed value shown, no lost keys | Very fast typing loses keys | B (conflicts with 7) | `upper-async`, `async-on-change-transform` |
| 12 | App sets a value equal to a pending typed value (e.g. resets to an earlier text while the state lags) | Written | Not written, taken as a lagging render | B (conflicts with 8) | None |
| 13 | Sync on-change rejects a change by keeping the state | Typed text reverted | Stays until the next new value | B (conflicts with 10) | `reject-sync`, `on-change-rejects-key` |
| 14 | Sync on-change transforms back to the current state (drop a 0, truncate, filter) | Transformed value written | Stays, like 13 | B (conflicts with 10) | `number-nozero-sync` |
| 15 | Async on-change rejects a change | Typed text reverted | Stays | B (conflicts with 10) | `reject-async`, `async-on-change-rejects-key` |
| 16 | on-change writes the last accepted value into the input | Rejected text removed, caret where the key was typed | Passes | C | `reject-restore-sync`, `on-change-restores-value*`, `async-on-change-restores-value` |
| 17 | on-change writes the input and moves the caret to the end itself | Caret at the end | Moved back to the old distance from the end | C (setter hook prototype, depends on React internals) | `on-change-moves-cursor-after-write` (prototype only) |
| 18 | React StrictMode remount | Same as without StrictMode | Passes | C | `strict-mode-*` |
| 19 | Input rendered by a JS component (`react/createElement "input"`) | Same as a Reagent input | Cursor jumps, lost keys, doubled IME letters | A (Reagent never sees the input) | `js-text-sync` |
| 20 | JS component rendering Reagent's `:input` (e.g. MUI `inputComponent`) | Same as a Reagent input | Passes | C | `js-reagent-text-sync` |

### Conflicts

- **13, 14, 15 vs 10:** after a change, the rendered value stays at the old
  value either because on-change rejected it or because the state hasn't
  caught up yet. Reverting fixes 13-15 and loses keys in 10, however long it
  waits. #690 picks 10. Resolving both needs the app to say which it is, see
  "Possible solutions" (reject API, opt-in, or reverting on blur, which
  avoids the key loss).
- **11 vs 7:** a lagging transformed value ("A" for typed "a") isn't one of
  the reported values, so it looks like a new value from the app. Writing it
  loses keys typed since; not writing it would break 7. Would need on-change
  to report the transformed value, or sync state.
- **12 vs 8:** a value from the app that equals a pending typed value looks
  like a lagging render. Writing it would break 8. Rare in practice.

### Reference: React's controlled inputs

React restores the controlled value right after the event, synchronously. That
passes 13-15 and breaks 8-10 (and 11) whenever the state is updated later,
the opposite trade-off from #690.
