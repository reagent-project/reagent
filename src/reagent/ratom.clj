(ns reagent.ratom
  (:refer-clojure :exclude [run!])
  (:require [reagent.debug :as d]
            [reagent.interop :as interop]))

(defmacro reaction [& body]
  `(reagent.ratom/make-reaction
    (fn [] ~@body)))

(defmacro run!
  "Runs body immediately, and runs again whenever atoms deferenced in the body change. Body should side effect."
  [& body]
  `(let [co# (reagent.ratom/make-reaction (fn [] ~@body)
                                         :auto-run true)]
     (deref co#)
     co#))

(defmacro with-let [bindings & body]
  (assert (vector? bindings)
          (str "with-let bindings must be a vector, not "
               (pr-str bindings)))
  (let [v (gensym "with-let")
        k (keyword v)
        init (gensym "init")
        bs (into [init `(zero? (alength ~v))]
                 (map-indexed (fn [i x]
                                (if (even? i)
                                  x
                                  (let [j (quot i 2)]
                                    `(if ~init
                                       (interop/unchecked-aset ~v ~j ~x)
                                       (interop/unchecked-aget ~v ~j)))))
                              bindings))
        [forms destroy] (let [fin (last body)]
                          (if (and (list? fin)
                                   (= 'finally (first fin)))
                            [(butlast body) `(fn [] ~@(rest fin))]
                            [body nil]))
        add-destroy (when destroy
                      `(let [destroy# ~destroy]
                         (if (reagent.ratom/reactive?)
                           (when (nil? (.-destroy ~v))
                             (set! (.-destroy ~v) destroy#))
                           (destroy#))))
        asserting (if *assert* true false)]
    `(let [~v (reagent.ratom/with-let-values ~k)]
       (when ~asserting
         (when-some [^clj c# reagent.ratom/*ratom-context*]
           (when (== (.-generation ~v) (.-ratomGeneration c#))
             (d/error "Warning: The same with-let is being used more "
                      "than once in the same reactive context."))
           (set! (.-generation ~v) (.-ratomGeneration c#))))
       (let ~bs
         (let [res# (do ~@forms)]
           ~add-destroy
           res#)))))
