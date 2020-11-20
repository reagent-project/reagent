(ns reagent.core
  (:require [reagent.ratom :as ra]
            cljs.analyzer.api))

(defmacro with-let
  "Bind variables as with let, except that when used in a component
  the bindings are only evaluated once. Also takes an optional finally
  clause at the end, that is executed when the component is
  destroyed."
  [bindings & body]
  `(ra/with-let ~bindings ~@body))

(defn- source-info [env]
  (when (:line env)
    {:file (try
             ((resolve 'cljs.analyzer.api/current-file))
             (catch Exception _
               ;; ana-api/current-file was added in 1.10.758
               cljs.analyzer/*cljs-file*))
     :line (:line env)
     :column (:column env)}))

(defmacro render
  "Render function was moved to reagent.dom namespace in 1.0"
  [& body]
  (throw (ex-info "reagent.core/render function was moved to reagent.dom namespace in Reagent v1.0"
                  (assoc (source-info &env) :tag :cljs/analysis-error)))
  nil)
