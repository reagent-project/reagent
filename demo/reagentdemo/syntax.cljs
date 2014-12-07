(ns reagentdemo.syntax
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


;; Function shortcuts to reduce code size a bit

(defn comment-span [v] [:span comment-style v])
(defn string-span [v] [:span string-style v])
(defn keyword-span [v] [:span keyword-style v])
(defn builtin-span [v] [:span builtin-style v])
(defn def-span [v] [:span def-style v])

(defn paren-span-1 [v] [:span paren-style-1 v])
(defn paren-span-2 [v] [:span paren-style-2 v])
(defn paren-span-3 [v] [:span paren-style-3 v])
