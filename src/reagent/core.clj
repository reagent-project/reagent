(ns reagent.core
  (:require [cljs.core :as core]
            [reagent.ratom :as ra]))

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

;; From uix.lib
(defn parse-sig [name fdecl]
  (let [[fdecl m] (if (string? (first fdecl))
                    [(next fdecl) {:doc (first fdecl)}]
                    [fdecl {}])
        [fdecl m] (if (map? (first fdecl))
                    [(next fdecl) (conj m (first fdecl))]
                    [fdecl m])
        fdecl (if (vector? (first fdecl))
                (list fdecl)
                fdecl)
        [fdecl m] (if (map? (last fdecl))
                    [(butlast fdecl) (conj m (last fdecl))]
                    [fdecl m])
        m (conj {:arglists (list 'quote (#'cljs.core/sigs fdecl))} m)
        m (conj (if (meta name) (meta name) {}) m)]
    [(with-meta name m) fdecl]))

(defmacro defc
  "Creates function component"
  [sym & fdecl]
  ;; Just use original fdecl always for render fn.
  ;; Parse for fname metadata.
  (let [[fname fdecl] (parse-sig sym fdecl)
        ;; FIXME: Should probably support multiple arities for components
        [args & fdecl] (first fdecl)
        var-sym (-> (str (-> &env :ns :name) "/" sym) symbol (with-meta {:tag 'js}))]
    `(do
       (def ~fname (reagent.impl.component/memo
                     (fn ~fname [jsprops#]
                       ;; FIXME: Replace functional-render with new function that takes renderFn as parameter.
                       ;; Need completely new component impl for that.
                       (let [render-fn# (fn ~'reagentRender ~args
                                          (when ^boolean goog.DEBUG
                                            (when-let [sig-f# (.-fast-refresh-signature ~var-sym)]
                                              (sig-f#)))
                                          ~@fdecl)
                             jsprops2# (js/Object.assign (core/js-obj "reagentRender" render-fn#) jsprops#)]
                         (reagent.impl.component/functional-render reagent.impl.template/*current-default-compiler* jsprops2#)))))
       (reagent.dev/register ~var-sym ~(str fname))
       (when ^boolean goog.DEBUG
         (let [sig# (reagent.dev/signature)]
           ;; Empty signature but set forceReset flag.
           (sig# ~var-sym "" true nil)
           (set! (.-fast-refresh-signature ~var-sym) sig#)))
       (set! (.-reagent-component ~fname) true)
       (set! (.-displayName ~fname) ~(str sym)))))

(comment
  (clojure.pprint/pprint (macroexpand-1 '(defc foobar [a b] (+ a b))))
  (clojure.pprint/pprint (clojure.walk/macroexpand-all '(defc foobar [a b] (+ a b)))))
