(ns reagentdemo.syntax
  (:require [clojure.string :as string]))

(def builtins #{"def" "defn" "ns" "atom" "let" "if" "when"
               "cond" "merge" "assoc" "swap!" "reset!" "for"
               "range" "nil?" "int" "or" "->" "->>" "%" "fn" "if-not"
               "empty?" "case" "str" "pos?" "zero?" "map" "remove"
               "empty" "into" "assoc-in" "dissoc" "get-in" "when-not"
               "filter" "vals" "count" "complement" "identity" "dotimes"
               "update-in" "sorted-map" "inc" "dec" "false" "true" "not"
               "=" "partial" "first" "second" "rest" "list" "conj"
               "drop" "when-let" "if-let" "add-watch"})

(def styles {:comment  {:style {:color "gray"
                                :font-style "italic"}}
             :str-litt {:style {:color "green"}}
             :keyw     {:style {:color "blue"}}
             :builtin  {:style {:font-weight "bold"
                                :color "#687868"}}
             :def      {:style {:color "#55c"
                                :font-weight "bold"}}})

(def paren-styles [{:style {:color "#272"}}
                   {:style {:color "#940"}}
                   {:style {:color "#44a"}}])

(defn tokenize [src]
  (let [ws " \\t\\n"
        open "[({"
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
    (loop [tokens (tokenize src)
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
                 (conj res [:span style val]))
          (apply vector :pre res))))))
