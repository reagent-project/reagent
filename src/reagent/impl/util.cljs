(ns reagent.impl.util
  (:require [reagent.debug :refer-macros [dbg log warn]]
            [reagent.interop :refer-macros [$ $!]]
            [clojure.string :as string]))

(def is-client (and (exists? js/window)
                    (-> js/window ($ :document) nil? not)))

(def ^:dynamic ^boolean *non-reactive* false)

;;; Props accessors

;; Misc utilities

(defn memoize-1 [f]
  (let [mem (atom {})]
    (fn [arg]
      (let [v (get @mem arg)]
        (if-not (nil? v)
          v
          (let [ret (f arg)]
            (swap! mem assoc arg ret)
            ret))))))

(def dont-camel-case #{"aria" "data"})

(defn capitalize [s]
  (if (< (count s) 2)
    (string/upper-case s)
    (str (string/upper-case (subs s 0 1)) (subs s 1))))

(defn dash-to-camel [dashed]
  (if (string? dashed)
    dashed
    (let [name-str (name dashed)
          [start & parts] (string/split name-str #"-")]
      (if (dont-camel-case start)
        name-str
        (apply str start (map capitalize parts))))))

(defn fun-name [f]
  (let [n (or (and (fn? f)
                   (or ($ f :displayName)
                       ($ f :name)))
              (and (implements? INamed f)
                   (name f))
              (let [m (meta f)]
                (if (map? m)
                  (:name m))))]
    (-> n
        str
        (clojure.string/replace "$" "."))))

(deftype PartialFn [pfn f args]
  Fn
  IFn
  (-invoke [_]
    (pfn))
  (-invoke [_ a]
    (pfn a))
  (-invoke [_ a b]
    (pfn a b))
  (-invoke [_ a b c]
    (pfn a b c))
  (-invoke [_ a b c d]
    (pfn a b c d))
  (-invoke [_ a b c d e]
    (pfn a b c d e))
  (-invoke [_ a b c d e f]
    (pfn a b c d e f))
  (-invoke [_ a b c d e f g]
    (pfn a b c d e f g))
  (-invoke [_ a b c d e f g h]
    (pfn a b c d e f g h))
  (-invoke [_ a b c d e f g h i]
    (pfn a b c d e f g h i))
  (-invoke [_ a b c d e f g h i j]
    (pfn a b c d e f g h i j))
  (-invoke [_ a b c d e f g h i j k]
    (pfn a b c d e f g h i j k))
  (-invoke [_ a b c d e f g h i j k l]
    (pfn a b c d e f g h i j k l))
  (-invoke [_ a b c d e f g h i j k l m]
    (pfn a b c d e f g h i j k l m))
  (-invoke [_ a b c d e f g h i j k l m n]
    (pfn a b c d e f g h i j k l m n))
  (-invoke [_ a b c d e f g h i j k l m n o]
    (pfn a b c d e f g h i j k l m n o))
  (-invoke [_ a b c d e f g h i j k l m n o p]
    (pfn a b c d e f g h i j k l m n o p))
  (-invoke [_ a b c d e f g h i j k l m n o p q]
    (pfn a b c d e f g h i j k l m n o p q))
  (-invoke [_ a b c d e f g h i j k l m n o p q r]
    (pfn a b c d e f g h i j k l m n o p q r))
  (-invoke [_ a b c d e f g h i j k l m n o p q r s]
    (pfn a b c d e f g h i j k l m n o p q r s))
  (-invoke [_ a b c d e f g h i j k l m n o p q r s t]
    (pfn a b c d e f g h i j k l m n o p q r s t))
  (-invoke [_ a b c d e f g h i j k l m n o p q r s t rest]
    (apply pfn a b c d e f g h i j k l m n o p q r s t rest))
  IEquiv
  (-equiv [_ other]
    (and (= f (.-f other)) (= args (.-args other))))
  IHash
  (-hash [_] (hash [f args])))

(defn make-partial-fn [f args]
  (->PartialFn (apply partial f args) f args))

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
      (assert (map? p1)
              (str "Property must be a map, not " (pr-str p1)))
      (merge-style p1 (merge-class p1 (merge p1 p2))))))


(def ^:dynamic *always-update* false)

(defn force-update [comp deep]
  (if deep
    (binding [*always-update* true]
      ($ comp forceUpdate))
    ($ comp forceUpdate)))
