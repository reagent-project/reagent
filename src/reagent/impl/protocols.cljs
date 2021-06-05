(ns reagent.impl.protocols)

(defprotocol Compiler
  (get-id [this])
  (parse-tag [this tag-name tag-value])
  (as-element [this x])
  (make-element [this argv component jsprops first-child]))

