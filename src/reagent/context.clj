(ns reagent.context)

(defmacro with-context
  "Defines a context consumer component.
   First argument should be a binding as in when-let, with the left value being a context identifier.
   Remaining arguments will be the render function of the component

   (reagent.context/with-context [{:keys [foo bar]} react-context-id]
     [:div ...])"
  [bindings & body]
  (assert (and (vector? bindings)
               (= 2 (count bindings)))
          "First argument must be a binding vector with 2 elements")
  (let [[left right] bindings]
    `[consumer {:context ~right}
      (fn [~left]
        ~@body)]))
