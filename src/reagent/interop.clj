(ns reagent.interop
  (:require [clojure.string :refer [join]]))

(defn- js-call [f args]
  (let [argstr (->> (repeat (count args) "~{}")
                    (join ","))]
    (list* 'js* (str "~{}(" argstr ")") f args)))

(defn- kwd [k]
  (if (keyword? k) (name k) k))

(defmacro oget
  ([o k]
     `(aget ~o ~(kwd k)))
  ([o k & ks]
     `(aget ~o ~@(map kwd (list* k ks)))))

(defmacro oset
  ([o k v]
     `(aset ~o ~(kwd k) ~v))
  ([o k1 k2 & ksv]
     `(aset ~o
            ~@(map kwd (list* k1 k2 (butlast ksv)))
            ~(last ksv))))

(defmacro odo
  [o k & args]
  (let [f (cond (not (vector? k)) (list 'aget o (kwd k))
                (empty? k) o
                :else (list* 'aget o (map kwd k)))]
    (js-call f args)))
