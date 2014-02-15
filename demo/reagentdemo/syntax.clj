(ns reagentdemo.syntax
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))

(defmacro get-source [srcfile]
  (let [s (if-not (keyword? srcfile)
            srcfile
            (-> srcfile
                namespace
                (string/replace #"[.]" "/")
                (str ".cljs")))]
    (-> s io/resource slurp)))
