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

The typed text stays visible while the state differs. To reject a change,
`:on-change` calls `r/reject-change!` (added in #690), which writes the value
the input had before the change back into it (`doc/ControlledInputs.md`,
"Rejecting a change"). It works with async state too.

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

### Give on-change the previous value (done: `r/reject-change!`)

Reagent already knows the DOM value before the change (`cljsDOMValue` before
`input-handle-change` updates it), including values the app set. User code
can't track this correctly without copying the pending-value logic: a local
"last accepted value" goes stale when the app sets the value (e.g. clears the
input).

#690 adds `(r/reject-change! e)`: `input-handle-change` binds the node and
the previous value while `:on-change` runs, and `reject-change!` writes the
previous value back (the cursor is kept by the code that handles writes from
`:on-change`). Outside such an `:on-change` it warns and does nothing.
InterMine's bluegenes has an app-side `inputs/reject-input!` for the same
purpose, used by 2 of about 108 inputs.

Not done: `:on-change` returning a marker (e.g. `false`) to reject the
change. A return value is easy to return by accident (e.g. from `when`).

### Transform the value in on-change: `r/transform-change!` (proposed)

The counterpart of `reject-change!` for transformed values, with a rule for
what the input shows while the user types.

**Rule:** `:on-change` decides synchronously what the input shows: the typed
text, the previous value (`r/reject-change!`) or a transformed value
(`r/transform-change!`). The input's value when `:on-change` returns is what
it reported. A later render of a reported value is the state catching up and
is never written. Any other rendered value comes from the app (e.g. Reset)
and is written.

**`(r/transform-change! e v)`:** writes `v` into the input during
`:on-change`, with the cursor handling that writes from `:on-change` already
get (`set-cursor-from-end!`), and records `v` as reported.

- `input-handle-change` currently pops the typed value from
  `cljsPendingValues` when `:on-change` wrote the input. For a transform it
  has to replace the last entry with the written value instead, so that the
  later render of `v` is found in the pending list. That is right for
  `reject-change!` too, which then becomes `transform-change!` with the
  previous value.

**What it fixes** (matrix rows):

- 11, async transform: `:on-change` upper-cases and dispatches. Now the later
  render of "A" isn't a reported value, so it is written and keys typed since
  are lost. With `transform-change!`, "A" is already in the input and pending,
  so the render is ignored. Moves 11 from B to C (with the API); the last case
  that loses keys.
- 14, transform back to the current state (drop a typed 0, truncate): the
  input shows the transformed value right away, no render needed.

**What it doesn't fix:**

- 4, email/number: no Selection API, the cursor can't be kept.
- Transforms done later, outside `:on-change` (e.g. in a re-frame event
  handler): they still look like values from the app. Document the rule: with
  async state, validate and transform in `:on-change`.
- 12, an app value equal to a pending typed value: still taken for a lagging
  render. Rare.

**Breaking changes:** none beyond #690. Sync transforms through the state keep
working without the function (the render comes before the next key), so only
async-state apps need it, and their transforms lose keys now anyway. The rule
only adds a requirement for async transforms.

**Work:** the pending-list change, the function with a docstring, a
CHANGELOG entry, a section in `doc/ControlledInputs.md`, unit tests, and an
`upper-async` e2e variant that uses it; the `upper-async` known issue would
become a passing spec.

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

### Caret cut off in a narrow input (#595, fixed in #690)

When Reagent writes a value with the cursor at the end of a text that
overflows the input, Chrome scrolls only until the caret position is inside
the input, cutting off the caret and part of the last character by 2-10px.
Native typing scrolls to the end of the scroll range.

`set-cursor-from-end!` (used for rendered values and for values `:on-change`
writes, including `r/reject-change!`) now sets `scrollLeft` to the end when
the cursor is at the end and the text overflows:

- The `scrollLeft` setter clamps to the scroll range, and the range includes
  padding, so `scrollWidth` works as "the end" with padding, borders and
  `box-sizing: border-box`. Measured with a probe: with these styles the
  transformed input ends exactly at the end, native typing 1px short of it.
- Right-to-left: the end of the text is on the left, where `scrollLeft` goes
  from 0 down to `-(scrollWidth - clientWidth)`. Setting `scrollWidth` scrolled
  to the start of the text, hiding the end. The code checks the computed
  `direction` (only when the cursor is at the end and the text overflows) and
  sets `-scrollWidth` for `rtl`. Covered by `narrow-upper-rtl-sync` (rtl with
  padding).
- Not checked: browsers other than Chromium, and `dir="auto"` (the computed
  `direction` should reflect it). Textareas also get `scrollLeft` set, which
  has no effect while their text wraps.

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
| 5 | Sync transform in a narrow input, typing past the width (also right-to-left, with padding) | Caret and last letter visible | Passes since #690: Reagent scrolls to the end when the cursor is at the end (Chrome left them outside, #595) | C | `narrow-upper-sync`, `narrow-upper-rtl-sync` |
| 6 | IME composition, sync and async state | No doubled or leftover letters | Passes | C | `ime-sync`, `ime-async` |
| 7 | App sets a new value (e.g. a reset button), also while on-change values are pending | Written | Passes | C | `state-changed-from-outside`, `async-on-change-external-change-while-pending` |
| 8 | Async state (a later tick), fast typing, renders between keys and updates | No lost keys, no writes | Passes | C | `text-async`, `async-on-change-*` |
| 9 | Async state, input re-rendered with the lagging value (parent re-render) | Ignored | Passes | C | `text-async-rerender`, `textarea-async-rerender`, `async-on-change-rerender-with-lagging-state` |
| 10 | Async state lagging longer than a frame (busy re-frame queue) | No lost keys | Passes since #690 | B (conflicts with 13-15) | `text-async-slow`, `textarea-async-slow` |
| 11 | Async transform (upper-case), fast typing | Transformed value shown, no lost keys | Very fast typing loses keys | B (conflicts with 7); C with `r/transform-change!` (proposed) | `upper-async`, `async-on-change-transform` |
| 12 | App sets a value equal to a pending typed value (e.g. resets to an earlier text while the state lags) | Written | Not written, taken as a lagging render | B (conflicts with 8) | None |
| 13 | Sync on-change rejects a change by keeping the state (doesn't call `r/reject-change!`) | Not supported: call `r/reject-change!` (row 16b). Typed text stays (or reverted on blur, if added) | Stays until the next new value | B (conflicts with 10) | `reject-sync`, `on-change-rejects-key` |
| 14 | Sync on-change transforms back to the current state (drop a 0, truncate, filter) | Not supported without an explicit call: `r/transform-change!` (proposed), or `r/reject-change!` when the result equals the current state. Typed text stays otherwise | Stays, like 13 | B (conflicts with 10); C with `r/transform-change!` | `number-nozero-sync` |
| 15 | Async on-change rejects a change (the later update drops it, on-change doesn't reject) | Not supported: reject in on-change with `r/reject-change!` (row 16b). Typed text stays (or reverted on blur, if added) | Stays | B (conflicts with 10) | `reject-async`, `async-on-change-rejects-key` |
| 16 | on-change writes the last accepted value into the input | Rejected text removed, caret where the key was typed | Passes | C | `reject-restore-sync`, `on-change-restores-value*`, `async-on-change-restores-value` |
| 16b | on-change calls `r/reject-change!` (sync and async state) | Value before the change restored, caret where the key was typed | Passes | C | `reject-change-sync`, `reject-change-async`, `on-change-reject-change`, `async-on-change-reject-change`, `reject-change-outside-on-change` |
| 17 | on-change writes the input and moves the caret to the end itself | Caret at the end | Moved back to the old distance from the end | C (setter hook prototype, depends on React internals) | `on-change-moves-cursor-after-write` (prototype only) |
| 18 | React StrictMode remount | Same as without StrictMode | Passes | C | `strict-mode-*` |
| 19 | Input rendered by a JS component (`react/createElement "input"`) | Same as a Reagent input | Cursor jumps, lost keys, doubled IME letters | A (Reagent never sees the input) | `js-text-sync` |
| 20 | JS component rendering Reagent's `:input` (e.g. MUI `inputComponent`) | Same as a Reagent input | Passes | C | `js-reagent-text-sync` |

Rows 13-15 (`reject-sync`, `number-nozero-sync`, `reject-async`): the value
is dropped or transformed back to the current state, by on-change itself
(sync) or by the later update (async, like a re-frame event handler that
leaves the db unchanged), without telling Reagent. Nothing re-renders, so
the typed text stays in the input until the user changes it or the app sets
another value. That is intended: Reagent can't tell whether on-change
updates the state synchronously, later or not at all, and clearing the text
would need a revert decided after on-change returns, which is what lost keys
(2.0.x reverted after the next render). Rejecting or transforming has to be
explicit in on-change (`reject-change-sync`, `reject-change-async`, the
proposed `r/transform-change!`); the only safe automatic clean-up would be
the restore on blur, or the opt-in revert for apps that say their state is
sync.

### Conflicts

- **13, 14, 15 vs 10:** after a change, the rendered value stays at the old
  value either because on-change rejected it or because the state hasn't
  caught up yet. Reverting fixes 13-15 and loses keys in 10, however long it
  waits. #690 picks 10 and makes rejecting explicit: `r/reject-change!` for
  13 and 15, `r/transform-change!` (proposed) or `r/reject-change!` for 14.
  See "Possible solutions" for the opt-in revert and reverting on blur.
- **11 vs 7:** a lagging transformed value ("A" for typed "a") isn't one of
  the reported values, so it looks like a new value from the app. Writing it
  loses keys typed since; not writing it would break 7. Would need on-change
  to report the transformed value (`r/transform-change!`, proposed above), or
  sync state.
- **12 vs 8:** a value from the app that equals a pending typed value looks
  like a lagging render. Writing it would break 8. Rare in practice.

### Reference: React's controlled inputs

React restores the controlled value right after the event, synchronously. That
passes 13-15 and breaks 8-10 (and 11) whenever the state is updated later,
the opposite trade-off from #690.
