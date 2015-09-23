(ns reagent.ratom
  (:refer-clojure :exclude [run!]))

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
  (assert (vector? bindings))
  (let [v (gensym "bind-v")
        bs (->> bindings
                (map-indexed (fn [i x]
                               (if (even? i)
                                 x
                                 (let [pos (quot i 2)]
                                   `(if (> (alength ~v) ~pos)
                                      (aget ~v ~pos)
                                      (aset ~v ~pos ~x))))))
                vec)
        [forms destroy] (let [fin (last body)]
                          (if (and (list? fin)
                                   (= 'finally (first fin)))
                            [(butlast body) `(fn [] ~@(rest fin))]
                            [body nil]))]
    `(let [destroy-obj# (cljs.core/js-obj)
           ~v (reagent.ratom/get-cached-values (quote ~v) destroy-obj#)]
       (when *assert*
         (when-some [c# reagent.ratom/*ratom-context*]
           (when (== (.-ratomGeneration c#)
                     (.-generation ~v))
             (js/console.error
              "The same with-let is being used more than once in the
              same reactive context."))
           (set! (.-generation ~v) (.-ratomGeneration c#))))
       (let ~bs
         (let [destroy# ~destroy
               res# (do ~@forms)]
           (if (reagent.ratom/reactive?)
             (set! (.-destroy destroy-obj#) destroy#)
             (destroy#))
           res#)))))
