(ns reagentdemo.news
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.debug :refer-macros [dbg println]]
            [reagentdemo.syntax :refer-macros [get-source]]
            [reagentdemo.common :as common :refer [demo-component title]]
            [todomvc :as todomvc]))

(def funmap (-> "reagentdemo/news.cljs" get-source common/fun-map))
(def src-for (partial common/src-for funmap))

(def state todomvc/todos)

(def undo-list (atom nil))

(defn undo []
  (let [undos @undo-list]
    (when-let [old (first undos)]
      (reset! state old)
      (reset! undo-list (rest undos)))))

(defn undo-button []
  (let [n (count @undo-list)]
    [:input {:type "button" :on-click undo
             :disabled (zero? n)
             :value (str "Undo (" n ")")}]))

(defn todomvc-with-undo []
  (add-watch state ::undo-watcher
             (fn [_ _ old-state _]
               (swap! undo-list conj old-state)))
  [:div
   [undo-button]
   [todomvc/todo-app]])

(defn undo-demo []
  [demo-component {:comp todomvc-with-undo
                   :src (src-for [:state :undo-list :undo :save-state
                                  :undo-button :todomvc-with-undo])}])

(def undo-demo-cleanup
  (with-meta undo-demo {:component-will-unmount
                        (fn []
                          (reset! undo-list nil)
                          (remove-watch state ::undo-watcher))}))

(defn main []
  (let [head "This should become news"]
    [:div.reagent-demo
     [:h1 head]
     [title head]
     [undo-demo-cleanup]]))
