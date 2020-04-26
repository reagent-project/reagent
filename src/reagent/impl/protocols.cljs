(ns reagent.impl.protocols)

(defprotocol Compiler
  (get-id [this])
  (as-element [this x])
  (make-element [this argv component jsprops first-child]))

