(ns demoutil
  (:require [clojure.string :as string]))

(def builtins #{"def" "defn" "ns" "atom" "let" "if" "when"
               "cond" "merge" "assoc" "swap!" "reset!" "for"
               "range" "nil?" "int" "or" "->" "%" "fn"})

(defn tokenize [src]
  (let [ws " \\t\\n"
        open "[({"
        close ")\\]}"
        str-p "\"[^\"]*\""
        sep (str ws open close)
        open-p (str "[" open "]")
        close-p (str "[" close "]")
        iden-p (str "[^" sep "]+")
        any-p (str "[" ws "]+" "|.")
        patt (re-pattern (str "("
                              (string/join ")|(" [str-p open-p close-p
                                                  iden-p any-p])
                              ")"))
        keyw-re #"^:"]
    (for [[s str-litt open close iden any] (re-seq patt src)]
      (cond
       str-litt [:str-litt s]
       open [:open s]
       close [:close s]
       iden (cond
             (re-find keyw-re s) [:keyw s]
             (builtins s) [:builtin s]
             :else [:iden s])
       any [:other s]))))

(defn syntaxify [src]
  (let [def-re #"^def|^ns\b"
        parcol ["#9a3" "#c83" "#4a8"]
        ncol (count parcol)
        paren-style (fn [level]
                      {:style {:color (nth parcol (mod level ncol))}})]
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
                    :str-litt {:style {:color "green"}}
                    :keyw     {:style {:color "blue"}}
                    :builtin  {:style {:font-weight "bold"}}
                    :iden     (when (and prev (re-find def-re prev))
                                {:style {:color "#55c"
                                         :font-weight "bold"}})
                    :open     (paren-style level)
                    :close    (paren-style level')
                    nil)
            remain (rest tokens)]
        (if-not (empty? remain)
          (recur remain
                 (if (= kind :other) prev val)
                 level'
                 (conj res [:span style val]))
          (apply vector :pre res))))))
