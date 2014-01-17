(ns reagent.impl.util
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))

(defmacro import-js [srcfile]
  (let [src# (slurp (io/resource srcfile))]
    `(js/eval ~(str src#
                   "\n//@ sourceURL=" srcfile "\n"))))

(defmacro expose-vars [vars]
  (let [exp# (map #(str "\n/** @expose */\nX." (name %) " = false;\n")
                  vars)]
    (list 'js* (str "(function () {\nvar X = {};"
                    (apply str exp#)
                    "})"))))

