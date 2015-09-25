(ns reagent.interop
  (:require [clojure.string :as string :refer [join]]
            [clojure.java.io :as io]))

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
                  (string/split #"\."))
        names (map munge names)]
    [field? names]))

(defmacro obj
  "Create a javascript object in a Closure-safe way. The arguments
  are expected to be key-value pairs, where the keys should be either
  keywords or strings.

  (obj :foo 1)
  is equivalent to
  (let [o (js-obj)]
    (set! (.-foo o) 1)
    o)
  except that it gives the same result under advanced compilation."
  [& args]
  (assert (= 0 (mod (count args) 2))
          "Even number of arguments expected")
  (let [munged (map-indexed (fn [i x]
                              (if (odd? i)
                                x
                                (if (keyword? x)
                                  (munge (name x))
                                  x)))
                            args)]
    `(cljs.core/js-obj ~@munged)))

(defmacro .'
  "Access member in a javascript object, in a Closure-safe way.
  'member' is assumed to be a field if it is a keyword or if
  the name starts with '-', otherwise the named function is
  called with the optional args.
  'member' may contain '.', to allow access in nested objects.
  If 'object' is a symbol it is not allowed contain '.'.

  (.' o :foo) is equivalent to (.-foo o), except that it gives
  the same result under advanced compilation.
  (.' o foo arg1 arg2) is the same as (.foo o arg1 arg2)."
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
  If 'object' is a symbol it is not allowed contain '.'.

  (.! o :foo 1) is equivalent to (set! (.-foo o) 1), except that it
  gives the same result under advanced compilation."
  [object field value]
  (let [[field names] (dot-args object field)]
    (assert field (str "Field name must start with - in " field))
    `(aset ~object ~@names ~value)))
