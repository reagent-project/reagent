(ns reagent.interop
  (:require [clojure.string :as string :refer [join]]))

; taken from cljs.core
; https://github.com/binaryage/cljs-oops/issues/14
(defmacro unchecked-aget
  ([array idx]
   (list 'js* "(~{}[~{}])" array idx))
  ([array idx & idxs]
   (let [astr (apply str (repeat (count idxs) "[~{}]"))]
     `(~'js* ~(str "(~{}[~{}]" astr ")") ~array ~idx ~@idxs))))

; taken from cljs.core
; https://github.com/binaryage/cljs-oops/issues/14
(defmacro unchecked-aset
  ([array idx val]
   (list 'js* "(~{}[~{}] = ~{})" array idx val))
  ([array idx idx2 & idxv]
   (let [n (dec (count idxv))
         astr (apply str (repeat n "[~{}]"))]
     `(~'js* ~(str "(~{}[~{}][~{}]" astr " = ~{})") ~array ~idx ~idx2 ~@idxv))))

(defn- js-call [f args]
  (let [argstr (->> (repeat (count args) "~{}")
                    (join ","))]
    (list* 'js* (str "~{}(" argstr ")") f args)))

(defn- dot-args [object member]
  (assert (or (symbol? member)
              (keyword? member))
          (str "Symbol or keyword expected, not " (pr-str member)))
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

(defonce $-warning
  (delay
    (try
      (cljs.util/debug-prn "WARNING: reagent.interop/$ has been deprecated. Consider using ClojureScript JS-interop forms or goog.object namespace instead.")
      (catch Exception _ nil))))

(defmacro $
  "Access member in a javascript object, in a Closure-safe way.
  'member' is assumed to be a field if it is a keyword or if
  the name starts with '-', otherwise the named function is
  called with the optional args.
  'member' may contain '.', to allow access in nested objects.
  If 'object' is a symbol it is not allowed contain '.'.

  ($ o :foo) is equivalent to (.-foo o), except that it gives
  the same result under advanced compilation.
  ($ o foo arg1 arg2) is the same as (.foo o arg1 arg2)."
  {:deprecated true}
  [object member & args]
  @$-warning
  (let [[field names] (dot-args object member)]
    (if field
      (do
        (assert (empty? args)
                (str "Passing args to field doesn't make sense: " member))
        `(unchecked-aget ~object ~@names))
      (js-call (list* 'reagent.interop/unchecked-aget object names) args))))

(defonce $!-warning
  (delay
    (try
      (cljs.util/debug-prn "WARNING: reagent.interop/$! has been deprecated. Consider using ClojureScript JS-interop forms or goog.object namespace instead.")
      (catch Exception _ nil))))

(defmacro $!
  "Set field in a javascript object, in a Closure-safe way.
  'field' should be a keyword or a symbol starting with '-'.
  'field' may contain '.', to allow access in nested objects.
  If 'object' is a symbol it is not allowed contain '.'.

  ($! o :foo 1) is equivalent to (set! (.-foo o) 1), except that it
  gives the same result under advanced compilation."
  {:deprecated true}
  [object field value]
  @$!-warning
  (let [[field names] (dot-args object field)]
    (assert field (str "Field name must start with - in " field))
    `(unchecked-aset ~object ~@names ~value)))
