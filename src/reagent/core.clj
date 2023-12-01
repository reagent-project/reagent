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

(defn- parse-sig
  "Parse doc-string, attr-map, and other metadata from the defn like arguments list."
  [name fdecl]
  (let [;; doc-string
        [fdecl m] (if (string? (first fdecl))
                    [(next fdecl) {:doc (first fdecl)}]
                    [fdecl {}])
        ;; attr-map
        [fdecl m] (if (map? (first fdecl))
                    [(next fdecl) (conj m (first fdecl))]
                    [fdecl m])
        ;; If single arity, wrap in one item list for next step
        fdecl (if (vector? (first fdecl))
                (list fdecl)
                fdecl)
        ;; If multi-arity, the last item could be an additional attr-map
        [fdecl m] (if (map? (last fdecl))
                    [(butlast fdecl) (conj m (last fdecl))]
                    [fdecl m])
        m (conj {:arglists (list 'quote (#'cljs.core/sigs fdecl))} m)
        ;; Merge with the meta from the original sym
        m (conj (if (meta name) (meta name) {}) m)]
    [(with-meta name m) fdecl]))

(defmacro defc
  "Create a Reagent function component

  The functions works like other components (defined using regular defn) when
  used inside hiccup elements (`[component]`), but it can't be used like a regular
  function. The created function is a React JS function component, i.e., it
  takes single js-props argument, and the function body is already wrapped to
  use Reagent implementation to work with Ratoms etc."
  {:arglists '([name doc-string? attr-map? [params*] prepost-map? body]
               [name doc-string? attr-map? ([params*] prepost-map? body) + attr-map?])}
  [sym & fdecl]
  (let [[fname fdecl] (parse-sig sym fdecl)]
    ;; Consider if :arglists should be replaced with [jsprops] or if that should be
    ;; included as one item?
    `(do
       (def ~fname (reagent.impl.component/memo
                     (fn ~sym [jsprops#]
                       (let [;; It is important that this fn is using the original name, so
                             ;; multi-arity definitions can call the other arities.
                             render-fn# (fn ~sym ~@fdecl)
                             jsprops2# (js/Object.assign (core/js-obj "reagentRender" render-fn#) jsprops#)]
                         (reagent.impl.component/functional-render reagent.impl.template/*current-default-compiler* jsprops2#)))))
       (set! (.-reagent-component ~fname) true)
       (set! (.-displayName ~fname) ~(str sym))
       (js/Object.defineProperty ~fname "name" (core/js-obj "value" ~(str sym) "writable" false)))))

(comment
  (clojure.pprint/pprint (macroexpand-1 '(defc foobar [a b] (+ a b))))
  (clojure.pprint/pprint (macroexpand-1 '(defc foobar "docstring" ([a] (foobar a nil)) ([a b] (+ a b)))))
  (clojure.pprint/pprint (clojure.walk/macroexpand-all '(defc foobar [a b] (+ a b)))))
