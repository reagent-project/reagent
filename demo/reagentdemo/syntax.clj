(ns reagentdemo.syntax
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [cljs.analyzer :as analyzer]))

;;; Source splitting

(defn src-parts [src]
  (string/split src #"\n(?=[(])"))

(defn src-defs [parts]
  (let [ws #"[^ \t\n]+"]
    (into {} (for [x parts]
               [(->> x (re-seq ws) second keyword) x]))))

(defn fun-map [src]
  (-> src src-parts src-defs))

(defn src-for-names [srcmap names]
  (string/join "\n" (map srcmap names)))


;;; Macros

(defmacro syntaxed [src]
  `(reagentdemo.syntax/syntaxify ~src))

;; ;; A much simpler way to find source: currently broken with #js annotations
;; (defmacro src-for [& syms]
;;   (let [s (map #(list 'with-out-str (list 'cljs.repl/source %)) syms)]
;;     `(->> [~@s]
;;           (string/join "\n")
;;           syntaxed)))

;; (defmacro src-from-file [f]
;;   (let [src (-> f io/resource slurp)]
;;     `(syntaxed ~src)))

(defmacro src-of
  ([funs]
   `(src-of ~funs nil))
  ([funs resource]
   (assert (or (nil? funs)
               (vector? funs)))
   (assert (or (nil? resource)
               (string? resource)))
   (let [f (if (nil? resource)
             (-> (name analyzer/*cljs-ns*)
                 (string/replace #"[.]" "/")
                 (str ".cljs"))
             resource)
         src (-> f io/resource slurp)
         sel (if (nil? funs)
               src
               (-> src fun-map (src-for-names funs)))]
     `(syntaxed ~sel))))
