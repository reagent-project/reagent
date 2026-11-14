(ns reagentdemo.syntax
  (:require-macros reagentdemo.syntax)
  (:require [sitetools.highlight :as hl]))

(defn syntaxify [src]
  (hl/highlight-hiccup src))
