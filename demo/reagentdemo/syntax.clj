(ns reagentdemo.syntax
  (:require [clojure.java.io :as io]))

(defmacro get-source [srcfile]
  (slurp (io/resource srcfile)))
