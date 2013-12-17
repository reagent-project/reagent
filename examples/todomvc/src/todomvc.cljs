
(ns todomvc
  (:require [cloact.core :as cloact :refer [atom partial]]
            [cloact.debug :refer-macros [dbg]]))

(defn todo-input-render [{:keys [title on-save on-stop]}]
  (let [val (atom title)
        stop (fn []
               (reset! val "")
               (if on-stop (on-stop)))
        save (fn []
               (let [v (-> @val str clojure.string/trim)]
                 (if-not (empty? v) (on-save v))
                 (stop)))]
    (fn [props]
      [:input (merge props
                     {:type "text" :value @val :on-blur save
                      :on-change #(reset! val (-> % .-target .-value))
                      :on-key-up #(case (.-which %)
                                    13 (save)
                                    27 (stop)
                                    nil)})])))

(def todo-input (with-meta todo-input-render
                  {:component-did-mount #(.focus (cloact/dom-node %))}))

(defn todo-item [{:keys [todo on-toggle on-save on-destroy]} this]
  (dbg "Rendering item")
  (let [{:keys [id done title]} todo
        {:keys [editing]} @this]
    [:li {:class (str (if done "completed ")
                      (if editing "editing"))}
     [:div.view
      [:input.toggle {:type "checkbox" :checked done :on-change on-toggle}]
      [:label {:on-double-click #(swap! this assoc :editing true)} title]
      [:button.destroy {:on-click on-destroy}]]
     (when editing
       [todo-input {:class "edit" :title title :on-save on-save
                    :on-stop #(swap! this assoc :editing false)}])]))

(defn todo-stats [{:keys [filter clear]}]
  (let [props-for (fn [name]
                    {:class (when (= name @filter) "selected")
                     :on-click #(reset! filter name)})]
    (fn [{:keys [active done]}]
      [:div
       [:span#todo-count
        [:strong active] " " (case active 1 "item" "items") " left"]
       [:ul#filters
        [:li [:a (props-for :all) "All"]]
        [:li [:a (props-for :active) "Active"]]
        [:li [:a (props-for :done) "Completed"]]]
       (when (pos? done)
         [:button#clear-completed {:on-click clear}
          "Clear completed " done])])))

(defn animation [props this]
  (let [TransitionGroup cloact/React.addons.TransitionGroup]
    [TransitionGroup {:transitionName (:name props)}
     (cloact/children this)]))

(def counter (atom 0))

(defn add-todo [todos text]
  (let [id (swap! counter inc)]
    (swap! todos assoc id {:id id :title text :done false})))

(defn toggle [todos id] (swap! todos update-in [id :done] not))
(defn save [todos id title] (swap! todos assoc-in [id :title] title))
(defn delete [todos id] (swap! todos dissoc id))

(defn mod-map [m f a] (->> m (f a) (into (empty m))))
(defn complete-all [todos v] (swap! todos mod-map map #(assoc-in % [1 :done] v)))
(defn clear-done [todos] (swap! todos mod-map remove #(get-in % [1 :done])))

(defn todo-app [props]
  (let [todos (or (:todos props)
                  (let [t (atom (sorted-map))]
                    (dotimes [x 5]
                      (add-todo t (str "Some todo " x)))
                    t))
        filt (atom :all)]
    (fn []
      (let [items (vals @todos)
            done (->> items (filter :done) count)
            active (- (count items) done)
            pred (case @filt
                     :active (complement :done)
                     :done :done
                     :all identity)]
        (dbg "Rendering main")
        [:section#todoapp
         [:header#header
          [:h1 "todos"]
          [todo-input {:id "new-todo" :placeholder "What needs to be done?"
                       :on-save (partial add-todo todos)}]]
         [:section#main
          [:input#toggle-all {:type "checkbox" :checked (zero? active)
                              :on-change (partial complete-all todos
                                                  (pos? active))}]
          [:label {:for "toggle-all"} "Mark all as complete"]
          [:ul#todo-list
           [animation {:name "todoitem"}
            (for [{id :id :as todo} (filter pred items)]
              [todo-item {:key id :todo todo
                          :on-save (partial save todos id)
                          :on-toggle (partial toggle todos id)
                          :on-destroy (partial delete todos id)}])]]]
         [:footer#footer
          [todo-stats {:active active :done done :filter filt
                       :clear (partial clear-done todos)}]]
         [:footer#info
          [:p "Double-click to edit a todo"]]]))))

(defn ^:export run []
  (cloact/render-component [todo-app] (.-body js/document)))
