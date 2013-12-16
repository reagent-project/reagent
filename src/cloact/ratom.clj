(ns cloact.ratom)

(defn extract-opts [forms]
  (let [opts (->> forms
                  (partition 2)
                  (take-while #(keyword? (first %)))
                  (apply concat))]
    [opts (drop (count opts) forms)]))

(defmacro reaction [& body]
  (let [[opts# main#] (extract-opts body)]
    `(cloact.ratom/make-reaction
      (fn [] ~@main#) ~@opts#)))

(defmacro run!
  "Runs body immediately, and runs again whenever atoms deferenced in the body change. Body should side effect."
  [& body]
  `(let [co# (reaction :auto-run true ~@body)]
     (deref co#)
     co#))
