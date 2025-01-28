(ns reagentdemo.news.undodemo
  (:require [reagent.core :as r]
            [reagentdemo.syntax :as s]
            [sitetools.core :as tools :refer [link]]
            [reagentdemo.common :as common :refer [demo-component]]
            [todomvc.core :as todomvc]))

(def url "/news/cloact-reagent-undo-demo.html")
(def title "Cloact becomes Reagent: Undo is trivial")

(def ns-src (s/syntaxed "(ns example
  (:require [reagent.core :as r]))"))

(def state todomvc/todos)

(def undo-list (r/atom nil))

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
                   :src [:pre ns-src
                         (s/src-of [:state :undo-list :undo :save-state
                                    :undo-button :todomvc-with-undo])]}])

(def undo-demo-cleanup
  (with-meta undo-demo {:component-will-unmount
                        (fn []
                          (reset! undo-list nil)
                          (remove-watch state ::undo-watcher))}))

(defn main [{:keys [summary]}]
  (let [head title]
    [:div.reagent-demo
     [:h1 [link {:href url} head]]
     ;; Undo demo originall added on 0a0dbdee7dfdaa545171d41fa1c3a18a1cae6e1b
     [:span "2014-01-19"]

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
        [link {:href url
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

(tools/register-page url [#'main] title)
