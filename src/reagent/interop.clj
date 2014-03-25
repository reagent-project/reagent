(ns reagent.interop
  (:require [clojure.string :as string :refer [join]]))

(defn- js-call [f args]
  (let [argstr (->> (repeat (count args) "~{}")
                    (join ","))]
    (list* 'js* (str "~{}(" argstr ")") f args)))

(defn- dot-args [object member]
  (assert (or (symbol? member)
              (keyword? member))
          (str "Symbol or keyword expected, not " member))
  (assert (or (not (symbol? object))
              (not (re-find #"\." (name object))))
          (str "Dot not allowed in " object))
  (let [n (name member)
        field? (or (keyword? member)
                   (= (subs n 0 1) "-"))
        names (-> (if (symbol? member)
                    (string/replace n #"^-" "")
                    n)
                  (string/split #"\."))]
    [field? names]))

(defmacro .'
  "Access member in a javascript object, in a Closure-safe way.
  'member' is assumed to be a field if it is a keyword or if
  the name starts with '-', otherwise the named function is
  called with the optional args.
  'member' may contain '.', to allow access in nested objects.
  If 'object' is a symbol it is not allowed contain '.'."
  [object member & args]
  (let [[field names] (dot-args object member)]
    (if field
      (do
        (assert (empty? args)
                (str "Passing args to field doesn't make sense: " member))
        `(aget ~object ~@names))
      (js-call (list* 'aget object names) args))))

(defmacro .!
  "Set field in a javascript object, in a Closure-safe way.
  'field' should be a keyword or a symbol starting with '-'.
  'field' may contain '.', to allow access in nested objects.
  If 'object' is a symbol it is not allowed contain '.'."
  [object field value]
  (let [[field names] (dot-args object field)]
    (assert field (str "Field name must start with - in " field))
    `(aset ~object ~@names ~value)))


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
