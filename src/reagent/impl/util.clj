(ns reagent.impl.util
  (:require [cljs.core :as core]))

(defn parse-sig
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
        m (conj {:arglists (list 'quote (#'core/sigs fdecl))} m)
        ;; Merge with the meta from the original sym
        m (conj (if (meta name) (meta name) {}) m)]
    [(with-meta name m) fdecl]))
