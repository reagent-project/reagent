(ns reagent.impl.util
  (:require [clojure.string :as string]
            [clojure.walk :refer [prewalk]]
            [goog.object :as gobj]
            [reagent.debug :refer-macros [dev?]]))

(def is-client (and (exists? js/window)
                    (-> (.-document js/window) nil? not)))

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

(defn dash-to-prop-name [dashed]
  (if (string? dashed)
    dashed
    (let [name-str (name dashed)
          [start & parts] (string/split name-str #"-")]
      (if (dont-camel-case start)
        name-str
        (apply str start (map capitalize parts))))))

(defn dash-to-method-name [dashed]
  (if (string? dashed)
    dashed
    (let [name-str (name dashed)
          name-str (string/replace name-str #"(unsafe|UNSAFE)[-_]" "UNSAFE_")
          [start & parts] (string/split name-str #"-")]
      (apply str start (map capitalize parts)))))

(defn fun-name [f]
  (let [n (or (and (fn? f)
                   (or (.-displayName f)
                       (let [n (.-name f)]
                         (if (and (string? n) (seq n))
                           n))))
              (and (implements? INamed f)
                   (name f))
              (let [m (meta f)]
                (if (map? m)
                  (:name m))))]
    (if n
      (string/replace (str n) "$" "."))))

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
  (-equiv [_ ^clj other]
    (and (instance? PartialFn other)
         (= f (.-f other))
         (= args (.-args other))))
  IHash
  (-hash [_] (hash [f args])))

(defn make-partial-fn [f args]
  (->PartialFn (apply partial f args) f args))

(defn ^boolean named? [x]
  (or (keyword? x)
      (symbol? x)))

(defn class-names
  ([])
  ([class]
   (if (coll? class)
     (let [classes (keep (fn [c]
                           (if c
                             (if (named? c)
                               (name c)
                               c)))
                         class)]
       (if (seq classes)
         (string/join " " classes)))
     (if (named? class)
       (name class)
       class)))
  ([a b]
   (if a
     (if b
       (str (class-names a) " " (class-names b))
       (class-names a))
     (class-names b)))
  ([a b & rst]
   (reduce class-names
           (class-names a b)
           rst)))

(defn- merge-class [p1 p2]
  (if (or (contains? p1 :class) (contains? p2 :class))
    (assoc p2 :class (class-names (:class p1) (:class p2)))
    p2))

(defn- merge-style [p1 p2]
  (let [style (when-let [s1 (:style p1)]
                (when-let [s2 (:style p2)]
                  (merge s1 s2)))]
    (if (nil? style)
      p2
      (assoc p2 :style style))))

(defn merge-props
  ([] nil)
  ;; Normalize :class even if there are no merging
  ([p]
   (if-let [c (:class p)]
     (assoc p :class (class-names c))
     p))
  ([p1 p2]
   (if (nil? p1)
     (if-let [c (:class p2)]
       (assoc p2 :class (class-names c))
       p2)
     (do
       (assert (map? p1)
               (str "Property must be a map, not " (pr-str p1)))
       (merge p1 (merge-style p1 (merge-class p1 p2))))))
  ([p1 p2 & ps]
   (reduce merge-props (merge-props p1 p2) ps)))

;; TODO: Doesn't look like correct place for this
(def ^:dynamic *always-update* false)

(defn force-update [^js/React.Component comp deep]
  (if deep
    (binding [*always-update* true]
      (.forceUpdate comp))
    (.forceUpdate comp)))

(defn shallow-obj-to-map [o]
  (let [ks (js-keys o)
        len (alength ks)]
    (loop [m {}
           i 0]
      (if (< i len)
        (let [k (aget ks i)]
          (recur (assoc m (keyword k) (gobj/get o k))
                 (inc i)))
        m))))

(defn ^boolean js-val? [x]
  (not (identical? "object" (goog/typeOf x))))

;; React key

(defn try-get-react-key [x]
  ;; try catch to avoid clojurescript peculiarity with
  ;; sorted-maps with keys that are numbers
  (try (get x :key)
       (catch :default e)))

(defn get-react-key [x]
  (when (map? x)
    (try-get-react-key x)))

;; NOTE: :r>/raw-element doesn't call this
;; NOTE: :>/native-element doesn't call this
;; But for both cases expand-seq-dev will use this for the dev time no-key
;; warning.

(defn react-key-from-vec [v]
  ;; Meta is a map always and is safe to read
  (let [k (:key (meta v))]
    (if (some? k)
      k
      (case (nth v 0 nil)
        ;; Handle special cases where properties map isn't the second children of the vector.
        ;; Third item is Clj props map.
        (:> :f>) (get-react-key (nth v 2 nil))
        ;; Third item is the JS props object
        :r> (some-> (nth v 2 nil) (.-key))
        ;; Regular html element or such, second item is Clj props map.
        ;; TODO: This is also called for component use, though the key won't be used?
        (get-react-key (nth v 1 nil))))))

;; Error messages

(defn- str-coll [coll]
  (if (dev?)
    (str (prewalk (fn [x]
                    (if (fn? x)
                      (let [n (fun-name x)]
                        (case n
                          ("" nil) x
                          (symbol n)))
                      x)) coll))
    (str coll)))

(defn hiccup-err [v comp-name & msg]
  (str (apply str msg) ": " (str-coll v) "\n" comp-name))
