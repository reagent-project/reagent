(ns reagent.interop)

(defn- js-call [f args]
  (let [argstr (->> (repeat (count args) "~{}")
                    (interpose ",")
                    (apply str))]
    (list* 'js* (str "~{}(" argstr ")") f args)))

(defn- from-keyword [k]
  (if (keyword? k)
    (name k)
    k))

(defn- get-names [k]
  (->> (if (vector? k) k [k])
       (map from-keyword)))

(defmacro get.
  [o k]
  `(aget ~o ~@(get-names k)))

(defmacro set.
  [o k v]
  `(aset ~o ~@(get-names k) ~v))

(defmacro call.
  [o k & args]
  (let [names (get-names k)
        f (if (empty? names)
            o
            (list* 'aget o names))]
    (js-call f args)))

(defmacro jval
  [s]
  (assert (keyword? s))
  (let [sym (symbol "js" (name s))]
    `(when (clojure.core/exists? ~sym)
       ~sym)))

