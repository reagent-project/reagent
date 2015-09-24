(ns reagent.core
  (:require [reagent.ratom :as ra]))

(defmacro with-let [bindings & body]
  `(ra/with-let ~bindings ~@body))
