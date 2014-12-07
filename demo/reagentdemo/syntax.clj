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

;;;;; Colorization

(def builtins #{"def" "defn" "ns" "atom" "let" "if" "when"
               "cond" "merge" "assoc" "swap!" "reset!" "for"
               "range" "nil?" "int" "or" "->" "->>" "%" "fn" "if-not"
               "empty?" "case" "str" "pos?" "zero?" "map" "remove"
               "empty" "into" "assoc-in" "dissoc" "get-in" "when-not"
               "filter" "vals" "count" "complement" "identity" "dotimes"
               "update-in" "sorted-map" "inc" "dec" "false" "true" "not"
               "=" "partial" "first" "second" "rest" "list" "conj"
               "drop" "when-let" "if-let" "add-watch" "mod" "quot"
               "bit-test" "vector"})

(def me "reagentdemo.syntax")

(def styles {:comment  (symbol me "comment-span")
             :str-litt (symbol me "string-span")
             :keyw     (symbol me "keyword-span")
             :builtin  (symbol me "builtin-span")
             :def      (symbol me "def-span")})

(def paren-styles [(symbol me "paren-span-1")
                   (symbol me "paren-span-2")
                   (symbol me "paren-span-3")])

(defn tokenize [src]
  (let [ws " \\t\\n"
        open "\\[({"
        close ")\\]}"
        sep (str ws open close)
        comment-p ";.*"
        str-p "\"[^\"]*\""
        open-p (str "[" open "]")
        close-p (str "[" close "]")
        iden-p (str "[^" sep "]+")
        meta-p (str "\\^" iden-p)
        any-p (str "[" ws "]+" "|\\^[^" sep "]+|.")
        patt (re-pattern (str "("
                              (string/join ")|(" [comment-p str-p open-p
                                                  close-p meta-p iden-p any-p])
                              ")"))
        keyw-re #"^:"]
    (for [[s comment str-litt open close met iden any] (re-seq patt src)]
      (cond
       comment [:comment s]
       str-litt [:str-litt s]
       open [:open s]
       close [:close s]
       met [:other s]
       iden (cond
             (re-find keyw-re s) [:keyw s]
             (builtins s) [:builtin s]
             :else [:iden s])
       any [:other s]))))

(defn syntaxify [src]
  (let [def-re #"^def|^ns\b"
        ncol (count paren-styles)
        paren-style (fn [level]
                      (nth paren-styles (mod level ncol)))]
    (loop [tokens (tokenize (str src " "))
           prev nil
           level 0
           res []]
      (let [[kind val] (first tokens)
            level' (case kind
                     :open (inc level)
                     :close (dec level)
                     level)
            style (case kind
                    :iden  (when (and prev (re-find def-re prev))
                             (:def styles))
                    :open  (paren-style level)
                    :close (paren-style level')
                    (styles kind))
            remain (rest tokens)]
        (if-not (empty? remain)
          (recur remain
                 (if (= kind :other) prev val)
                 level'
                 (conj res (if (nil? style)
                             val
                             (list style val))))
          (apply vector :pre res))))))

;;;; Source splitting

(defn src-parts [src]
  (string/split src #"\n(?=[(])"))

(defn src-defs [parts]
  (let [ws #"[^ \t]+"]
    (into {} (for [x parts]
               [(->> x (re-seq ws) second keyword) x]))))

(defn fun-map [src]
  (-> src src-parts src-defs))

(defn src-for-names [srcmap names]
  (string/join "\n" (map srcmap names)))

;;; Macros

(defmacro syntaxed [src]
  (assert (string? src))
  (syntaxify src))

(defmacro src-of
  ([funs]
   `(src-of ~funs nil))
  ([funs resource]
   (assert (or (nil? funs)
               (vector? funs)))
   (assert (or (nil? resource)
               (string? resource)))
   (let [f (if (nil? resource)
             (-> (name cljs.analyzer/*cljs-ns*)
                 (string/replace #"[.]" "/")
                 (str ".cljs"))
             resource)
         src (-> f io/resource slurp)
         fm (fun-map src)
         sel (if (nil? funs)
               src
               (-> src fun-map (src-for-names funs)))]
     (syntaxify sel))))
