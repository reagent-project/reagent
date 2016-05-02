(ns reagent.impl.util
  (:require [cljsjs.react]
            [reagent.debug :refer-macros [dbg log warn]]
            [reagent.interop :refer-macros [$ $!]]
            [clojure.string :as string]))

(defonce react
  (cond (exists? js/React) js/React
        (exists? js/require) (or (js/require "react")
                                 (throw (js/Error. "require('react') failed")))
        :else (throw (js/Error. "js/React is missing"))))

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


(def ^:dynamic *always-update* false)

(defn force-update [comp deep]
  (if deep
    (binding [*always-update* true]
      ($ comp forceUpdate))
    ($ comp forceUpdate)))
