(ns reagent.core
  (:require [reagent.ratom :as ra]))

(defmacro with-let
  "Bind variables as with let, except that when used in a component
  the bindings are only evaluated once. Also takes an optional finally
  clause at the end, that is executed when the component is
  destroyed."
  [bindings & body]
  `(ra/with-let ~bindings ~@body))

(defmacro reaction
  "Creates Reaction from the body, returns a derefable
  containing the result of the body. If the body derefs
  reactive values (Reagent atoms, track, etc), the body
  will run again and the value of the Reaction is updated.

  New Reaction is created everytime reaction is called,
  so caller needs to take care that new reaction isn't created
  e.g. every component render, by using with-let, form-2 or form-3
  components or other solutions. Consider using reagent.core/track,
  for function that caches the derefable value, and can thus be safely
  used in e.g. render function safely."
  [& body]
  `(reagent.ratom/make-reaction
    (fn [] ~@body)))
