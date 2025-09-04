# Reagent example app

`npx shadow-cljs watch app`

For testing input workaround with inputs in Reagent apps inside shadow-dom.

NOTE: with reagent.dom.client, it could be the cursor doesn't break if
flushSync is enabled from rdomc/render or hydrate-root calls?

Try commenting the `(set! batch/react-flush react-dom/flushSync)` away
to test the input workaround.
