(ns reagentdemo.news.undodemo
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.interop :refer-macros [.' .! fvar]]
            [reagent.debug :refer-macros [dbg println]]
            [reagentdemo.syntax :refer-macros [get-source]]
            [reagentdemo.page :refer [title link page-map]]
            [reagentdemo.common :as common :refer [demo-component]]
            [todomvc :as todomvc]))

(def funmap (-> ::this get-source common/fun-map))
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

(defn main [{:keys [summary]}]
  (let [head "Cloact becomes Reagent: Undo is trivial"]
    [:div.reagent-demo
     [:h1 [link {:href (fvar main)} head]]
     [title head]
     [:div.demo-text
      [:h2 "(reset! cloact-name \"Reagent\")"]

      [:p "It turns out that ”Cloact” was a really, really bad
      name. It made some people think about birds’ behinds, in
      possibly unhealthy ways, which even Google suggested they
      should."]

      [:p "The new name is " [:strong "Reagent"] ", which hopefully
      doesn’t bring with it the same disgusting connotations."]

      [:p "The API is otherwise unchanged, so a simple
      search-and-replace should suffice."]

      (if summary
        [link {:href (fvar main)
               :class 'news-read-more} "Read more"]
        [:div.demo-text

         [:h2 "Undo the easy way"]

         [:p "To celebrate the undoing of the apparently disgusting
         name, here is an example of how easy it is to add undo
         functionality to Reagent components."]

         [:p "It simply saves the old state whenever it changes, and
         restores it when the button is clicked."]

         [:p "The really nice thing about ClojureScript is that not
         only is this easy and safe to do, courtesy of immutable data
         structures, it is also efficient. ClojureScript figures out
         how to represent ”changes” to maps and vectors efficiently,
         so that you won’t have to."]

         [undo-demo-cleanup]])]]))

(swap! page-map assoc
       "news/cloact-reagent-undo-demo.html" (fvar main))
