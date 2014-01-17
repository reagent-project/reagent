(ns reagent.impl.util
  (:require [reagent.debug :refer-macros [dbg]]))

(deftype partial-ifn [f args ^:mutable p]
  IFn
  (-invoke [_ & a]
    (or p (set! p (apply clojure.core/partial f args)))
    (apply p a))
  IEquiv
  (-equiv [_ other]
    (and (= f (.-f other)) (= args (.-args other))))
  IHash
  (-hash [_] (hash [f args])))

(defn- merge-class [p1 p2]
  (let [class (when-let [c1 (:class p1)]
                (when-let [c2 (:class p2)]
                  (str c1 " " c2)))]
    (if (nil? class)
      p2
      (assoc p2 :class class))))

(defn- merge-style [p1 p2]
  (let [style (when-let [s1 (:style p1)]
                (when-let [s2 (:style p2)]
                  (merge s1 s2)))]
    (if (nil? style)
      p2
      (assoc p2 :style style))))

(defn merge-props [p1 p2]
  (if (nil? p1)
    p2
    (do
      (assert (map? p1))
      (merge-style p1 (merge-class p1 (merge p1 p2))))))

(defn identical-parts [v1 v2]
  ;; Compare two vectors using identical?
  (or (identical? v1 v2)
      (let [end (count v1)]
        (and (== end (count v2))
             (loop [n 0]
               (if (>= n end)
                 true
                 (if (identical? (nth v1 n) (nth v2 n))
                   (recur (inc n))
                   false)))))))

(def -not-found (js-obj))

(defn shallow-equal-maps [x y]
  ;; Compare two maps, using keyword-identical? on all values
  (or (identical? x y)
      (and (== (count x) (count y))
           (reduce-kv (fn [res k v]
                        (let [yv (get y k -not-found)]
                          (if (or (keyword-identical? v yv)
                                  ;; hack to allow reagent.core/partial and :style
                                  ;; maps to be compared with =
                                  (and (or
                                        (keyword-identical? k :style)
                                        (identical? (type v) partial-ifn))
                                       (= v yv)))
                            res
                            (reduced false))))
                      true x))))

(defn equal-args [p1 c1 p2 c2]
  [p1 c1 p2 c2]
  (and (identical-parts c1 c2)
       (shallow-equal-maps p1 p2)))
