(ns cloact.impl.util)

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

(defn identical-parts [v1 v2 from]
  ;; Compare two vectors, from item with index "from", using identical?
  (let [end (count v1)]
    (loop [n from]
      (if (>= n end)
        true
        (if (identical? (nth v1 n) (nth v2 n))
          (recur (inc n))
          false)))))

(def -not-found (js-obj))

(defn shallow-equal-maps [x y]
  ;; Compare two maps, using identical? on all values
  (or (identical? x y)
      (when (== (count x) (count y))
        (reduce-kv (fn [res k v]
                     (let [yv (get y k -not-found)]
                       (if (or (identical? v yv)
                               ;; hack to allow cloact.core/partial
                               (and (ifn? v) (= v yv)))
                         res
                         (reduced false))))
                   true x))))

(defn equal-args [v1 v2]
  ;; Compare two "args" vectors, i.e things like [:div {:foo "bar} "baz"],
  ;; using identical? on all individual parts.
  (or (identical? v1 v2)
      (let [c1 (count v1)]
        (and (= (nth v1 0) (nth v2 0)) ; may be symbol or fn
             (identical? c1 (count v2))
             (if (< c1 2)
               true
               (let [props1 (nth v1 1)]
                 (if (or (nil? props1) (map? props1))
                   (and (identical-parts v1 v2 2)
                        (shallow-equal-maps props1 (nth v2 1)))
                   (identical-parts v1 v2 1))))))))
