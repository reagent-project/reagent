(ns todomvc.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            ["react" :as react]
            [clojure.string :as str]))

(defonce todos (r/atom (sorted-map)))

(defonce counter (r/atom 0))

(defn add-todo [text]
  (let [id (swap! counter inc)]
    (swap! todos assoc id {:id id :title text :done false})))

(defn toggle [id] (swap! todos update-in [id :done] not))
(defn save [id title] (swap! todos assoc-in [id :title] title))
(defn delete [id] (swap! todos dissoc id))

(defn mmap [m f a] (->> m (f a) (into (empty m))))
(defn complete-all [v] (swap! todos mmap map #(assoc-in % [1 :done] v)))
(defn clear-done [] (swap! todos mmap remove #(get-in % [1 :done])))

(defonce init (do
                (add-todo "Rename Cloact to Reagent")
                (add-todo "Add undo demo")
                (add-todo "Make all rendering async")
                (add-todo "Allow any arguments to component functions")
                (complete-all true)))

(defn todo-input [{:keys [title on-save on-stop input-ref]}]
  (let [val (r/atom title)]
    (fn [{:keys [id class placeholder]}]
      (let [stop (fn [_e]
                   (reset! val "")
                   (when on-stop (on-stop)))
            save (fn [e]
                   (let [v (-> @val str str/trim)]
                     (when-not (empty? v)
                       (on-save v))
                     (stop e)))]
        [:input {:type "text"
                 :value @val
                 :ref input-ref
                 :id id
                 :class class
                 :placeholder placeholder
                 :on-blur save
                 :on-change (fn [e]
                              (reset! val (-> e .-target .-value)))
                 :on-key-down (fn [e]
                                (case (.-which e)
                                  13 (save e)
                                  27 (stop e)
                                  nil))}]))))

(defn todo-edit [props]
  (let [ref (react/useRef)]
    (react/useEffect (fn []
                       (.focus (.-current ref))
                       js/undefined))
    [todo-input (assoc props :input-ref ref)]))

(defn todo-stats [{:keys [filt active done]}]
  (let [props-for (fn [name]
                    {:class (when (= name @filt) "selected")
                     :on-click #(reset! filt name)})]
    [:div
     [:span#todo-count
      [:strong active] " " (case active 1 "item" "items") " left"]
     [:ul#filters
      [:li [:a (props-for :all) "All"]]
      [:li [:a (props-for :active) "Active"]]
      [:li [:a (props-for :done) "Completed"]]]
     (when (pos? done)
       [:button#clear-completed {:on-click clear-done}
        "Clear completed " done])]))

(defn todo-item []
  (let [editing (r/atom false)]
    (fn [{:keys [id done title]}]
      [:li
       {:class [(when done "completed ")
                (when @editing "editing")]}
       [:div.view
        [:input.toggle
         {:type "checkbox"
          :checked done
          :on-change #(toggle id)}]
        [:label
         {:on-double-click #(reset! editing true)}
         title]
        [:button.destroy {:on-click #(delete id)}]]
       (when @editing
         [:f> todo-edit {:class "edit"
                         :title title
                         :on-save #(save id %)
                         :on-stop #(reset! editing false)}])])))

(defn todo-app []
  (let [filt (r/atom :all)]
    (fn []
      (let [items (vals @todos)
            done (->> items (filter :done) count)
            active (- (count items) done)]
        [:div
         [:section#todoapp
          [:header#header
           [:h1 "todos"]
           [todo-input {:id "new-todo"
                        :placeholder "What needs to be done?"
                        :on-save add-todo}]]
          (when (-> items count pos?)
            [:div
             [:section#main
              [:input#toggle-all {:type "checkbox" :checked (zero? active)
                                  :on-change #(complete-all (pos? active))}]
              [:label {:for "toggle-all"} "Mark all as complete"]
              [:ul#todo-list
               (for [todo (filter (case @filt
                                    :active (complement :done)
                                    :done :done
                                    :all identity) items)]
                 ^{:key (:id todo)} [todo-item todo])]]
             [:footer#footer
              [todo-stats {:active active :done done :filt filt}]]])]
         [:footer#info
          [:p "Double-click to edit a todo"]]]))))

(defn ^:export run []
  (rdom/render [todo-app] (js/document.getElementById "app")))
