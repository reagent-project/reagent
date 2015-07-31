(ns reagentdemo.syntax
  (:require-macros reagentdemo.syntax)
  (:require [clojure.string :as string]))

;; Styles for syntax highlighting

(def comment-style {:style {:color "gray"
                            :font-style "italic"}})
(def string-style {:style {:color "green"}})
(def keyword-style {:style {:color "blue"}})
(def builtin-style {:style {:font-weight "bold"
                            :color "#687868"}})
(def def-style {:style {:color "#55c"
                        :font-weight "bold"}})

(def paren-style-1 {:style {:color "#272"}})
(def paren-style-2 {:style {:color "#940"}})
(def paren-style-3 {:style {:color "#44a"}})


;;;;; Colorization

(def builtins #{"def" "defn" "defonce" "ns" "atom" "let" "if" "when"
               "cond" "merge" "assoc" "swap!" "reset!" "for"
               "range" "nil?" "int" "or" "->" "->>" "%" "fn" "if-not"
               "empty?" "case" "str" "pos?" "zero?" "map" "remove"
               "empty" "into" "assoc-in" "dissoc" "get-in" "when-not"
               "filter" "vals" "count" "complement" "identity" "dotimes"
               "update-in" "sorted-map" "inc" "dec" "false" "true" "not"
               "=" "partial" "first" "second" "rest" "list" "conj"
               "drop" "when-let" "if-let" "add-watch" "mod" "quot"
               "bit-test" "vector"})

(def styles {:comment  comment-style
             :str-litt string-style
             :keyw     keyword-style
             :builtin  builtin-style
             :def      def-style})

(def paren-styles [paren-style-1 paren-style-2 paren-style-3])

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
        keyw-re #"^:"
        qualif-re #"^r/"]
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
             (re-find qualif-re s) [:builtin s]
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
                 (if (nil? style)
                   (let [old (peek res)]
                     (if (string? old)
                       (conj (pop res) (str old val))
                       (conj res val)))
                   (conj res [:span style val])))
          (apply vector :pre res))))))
