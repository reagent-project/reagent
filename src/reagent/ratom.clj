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

(defmacro with-resource [bindings & body]
  (assert (vector? bindings))
  (let [v (gensym "res-v")
        init (gensym "res-init-v")
        bs (into [] (map-indexed (fn [i x]
                                   (if (even? i)
                                     x
                                     (let [pos (-> (/ i 2) int)]
                                       `(if ~init
                                          (aget ~v ~pos)
                                          (aset ~v ~pos ~x)))))
                                 bindings))
        [forms destroy] (let [fin (last body)]
                          (if (and (list? fin)
                                   (= 'finally (first fin)))
                            [(butlast body) `(fn [] ~@(rest fin))]
                            [body nil]))]
    `(let [o# (cljs.core/js-obj)
           ~v (reagent.ratom/get-cached-values (quote ~v) o#)
           ~init (if (true? (.-init ~v))
                   true
                   (do
                     (set! (.-init ~v) true)
                     false))]
       (let ~bs
         (let [dest# ~destroy
               res# (do ~@forms)]
           (if (reagent.ratom/reactive?)
             (set! (.-destroy o#) ~destroy)
             (dest#))
           res#)))))
