# React 19 and Reagent

Starting with React 18 when using `createRoot` API, React will batch (i.e., queue)
updates:

> Starting in React 18 with createRoot, all updates will be automatically batched, no matter where they originate from.

https://github.com/reactwg/react-18/discussions/21

## Reagent batching

Reagent has already had a mechanism to queue and batch updates from Reagent Atom
changes since version 0.3.0 from February 2014. This means Reagent will collect
any changes in Ratoms during one animation frame, and then send them all together
to React in one batch.

This has many benefits, like updating one Ratom multiple times inside the regular
16.7ms window, doesn't trigger multiple React or DOM updates.

## React batching

Now React implements similar batching, to get the same benefits for hook and
other state mechanisms.

## What does this mean for Reagent?

When an Ratom changes, Reagent queues a change into its own queue.
After animation frame callback is triggered, Reagent will flush that queue into
React component re-render calls. With new React batching, this update will then
go into React queue, and it will be some time before those updates are
flushed into DOM.

In some cases this can mean that there will be small extra latency before the
change is visible to the user. At the maximum this should take one
extra animation frame, but I don't know how exactly React batching works.

Additional complication was that Reagent test-suite wasn't built to
work with "asynchronous rendering", so it wasn't easy to be sure if the
Reagent worked correctly (i.e., the test suite passed) with React 19.
This was solved with new test utils that wait for changes to be
flushed to DOM before running test assertions, and rewriting all test
cases to use these new test utils.

## Reagent mitigation

React provides [flushSync](https://react.dev/reference/react-dom/flushSync)
call to force any updates created inside provided callback to be flushed to
DOM immediately. They don't recommend using it in general.

To mitigate potential extra latency on Ratom updates, Reagent will
flush its Ratom update queue using `flushSync`. This means that any
Ratom updates should be rendered into DOM as fast as with previous Reagent
and React versions.

This mitigation doesn't have any effect on React Hook state or any other
state mechanisms.

I think the React warnings about bad performance due to `flushSync` use don't
matter for this use case, because Reagent updates are already batched.
