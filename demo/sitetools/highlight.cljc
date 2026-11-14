(ns sitetools.highlight
  "Minimal Clojure syntax highlighting. Used at runtime for the live examples
  (as hiccup) and at compile time for Markdown code blocks (as HTML)."
  (:require [clojure.string :as string]))

(def builtins #{"def" "defn" "defonce" "ns" "atom" "let" "if" "when"
                "cond" "merge" "assoc" "swap!" "reset!" "for"
                "range" "nil?" "int" "or" "->" "->>" "%" "fn" "if-not"
                "empty?" "case" "str" "pos?" "zero?" "map" "remove"
                "empty" "into" "assoc-in" "dissoc" "get-in" "when-not"
                "filter" "vals" "count" "complement" "identity" "dotimes"
                "update-in" "sorted-map" "inc" "dec" "false" "true" "not"
                "=" "partial" "first" "second" "rest" "list" "conj"
                "drop" "when-let" "if-let" "add-watch" "mod" "quot"
                "bit-test" "vector" "do" "try" "catch" "finally" "nil"
                "with-let" "defc" "deref" "fn*" "loop" "recur" "doseq"
                "require" "update" "get" "some?" "when-some" "if-some"})

(def tokenize-pattern
  (let [ws        " \\t\\n,"
        open      "\\[({"
        close     ")\\]}"
        sep       (str ws open close)
        comment-p ";.*"
        str-p     "\"(?:[^\"\\\\]|\\\\.)*\""
        open-p    (str "#?[" open "]")
        close-p   (str "[" close "]")
        iden-p    (str "[^" sep "]+")
        meta-p    (str "\\^" iden-p)
        any-p     (str "[" ws "]+|.")]
    (re-pattern (str "("
                     (string/join ")|(" [comment-p str-p open-p
                                         close-p meta-p iden-p any-p])
                     ")"))))

(defn tokenize [src]
  (for [[s comment strlitt open close met iden _any]
        (re-seq tokenize-pattern src)]
    (cond
      (some? comment) [:comment s]
      (some? strlitt) [:string s]
      (some? open)    [:open s]
      (some? close)   [:close s]
      (some? met)     [:meta s]
      (some? iden)    (cond (string/starts-with? s ":") [:keyword s]
                            (re-find #"^-?\d" s) [:number s]
                            (builtins s) [:builtin s]
                            (re-find #"^[a-z]+/" s) [:builtin s]
                            :else [:iden s])
      :else           [:other s])))

(defn highlight
  "Returns [[class-or-nil text] ...]."
  [src]
  (loop [tokens (tokenize src)
         prev nil
         level 0
         res []]
    (if-some [[kind s] (first tokens)]
      (let [level' (case kind :open (inc level) :close (dec level) level)
            cls (case kind
                  :iden (when (and prev (re-find #"^def|^ns$" prev)) "hl-def")
                  :open (str "hl-p" (mod level 3))
                  :close (str "hl-p" (mod level' 3))
                  (:other :meta) nil
                  (str "hl-" (name kind)))
            res (if (and (nil? cls) (nil? (first (peek res))) (seq res))
                  (conj (pop res) [nil (str (second (peek res)) s)])
                  (conj res [cls s]))]
        (recur (rest tokens) (if (= :other kind) prev s) level' res))
      res)))

(defn escape-html [s]
  (-> s
      (string/replace "&" "&amp;")
      (string/replace "<" "&lt;")
      (string/replace ">" "&gt;")
      (string/replace "\"" "&quot;")))

(defn highlight-html [src]
  (apply str (for [[cls s] (highlight src)]
               (if cls
                 (str "<span class=\"" cls "\">" (escape-html s) "</span>")
                 (escape-html s)))))

(defn highlight-hiccup [src]
  (into [:pre.hl]
        (for [[cls s] (highlight src)]
          (if cls [:span {:class cls} s] s))))
