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

(def react-import-ns (atom nil))

(defmacro import-react
  []
  "Import React.js.
  This can be used instead of adding :preamble in project.clj
  (or adding react.js in a script tag). This may be more convenient when
  using :optimizations :none, since that doesn't take :preamble into account.
  Imports minimized version of React if :elide-asserts is true."
  (if-not (or (nil? @react-import-ns)
              (= *ns* @react-import-ns))
    ;; React was already imported in another namespace; so we avoid
    ;; duplicate imports.
    true
    (let [srcfile (if *assert* "reagent/react.js"
                    "reagent/react.min.js")
          src (slurp (io/resource srcfile))]
      (if (nil? @react-import-ns)
        (reset! react-import-ns *ns*))
      `(js/eval ~(str "if (typeof React != 'undefined' &&
                      typeof console != 'undefined') {
                      console.log('WARNING: React is already defined');
                      }"
                      src "; \n"
                      "if (typeof module != 'undefined' &&
                      typeof global != 'undefined' &&
                      module.exports && module.exports.DOM) {
                      global.React = module.exports;
                      } \n
                      //@ sourceURL=" srcfile "\n")))))


(defmacro fvar
  [f]
  (assert (symbol? f))
  (let [fns (or (namespace f)
                (str *ns*))
        fref (str *ns* "/" f)]
    `(let [f# (aget reagent.interop/fvars ~fref)]
       (if-not (nil? f#)
         f#
         (do
           (assert (not (nil? ~f))
                   ~(str "undefined fn: " f))
           (let [old# (aget ~f "-fvar")
                 v# (if (not (nil? old#))
                      old#
                      (doto #(.apply ~f nil (~'js* "arguments"))
                        (aset "name" (.-name ~f))
                        (aset "fvar" true)))]
             (aset ~f "-fvar" v#)
             (aset reagent.interop/fvars ~fref v#)))))))

(defmacro fvar?
  [f]
  `(and (fn? ~f)
        (not (nil? (aget ~f "fvar")))))
