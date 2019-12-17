(ns example.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            ;; FIXME: add global-exports support
            [cljsjs.react-sortable-hoc]
            [goog.object :as gobj]))

;; Adapted from https://github.com/clauderic/react-sortable-hoc/blob/master/examples/drag-handle.js#L10

(def DragHandle
  (js/SortableHOC.SortableHandle.
    ;; Alternative to r/reactify-component, which doens't convert props and hiccup,
    ;; is to just provide fn as component and use as-element or create-element
    ;; to return React elements from the component.
    (fn []
      (r/as-element [:span "::"]))))

(def SortableItem
  (js/SortableHOC.SortableElement.
    (r/reactify-component
      (fn [{:keys [value]}]
        [:li
         [:> DragHandle]
         value]))))

;; Alternative without reactify-component
;; props is JS object here
#_
(def SortableItem
  (js/SortableHOC.SortableElement.
    (fn [props]
      (r/as-element
        [:li
         [:> DragHandle]
         (.-value props)]))))

(def SortableList
  (js/SortableHOC.SortableContainer.
    (r/reactify-component
      (fn [{:keys [items]}]
        [:ul
         (for [[value index] (map vector items (range))]
           ;; No :> or adapt-react-class here because that would convert value to JS
           (r/create-element
             SortableItem
             #js {:key (str "item-" index)
                  :index index
                  :value value}))]))))

(defn vector-move [coll prev-index new-index]
  (let [items (into (subvec coll 0 prev-index)
                    (subvec coll (inc prev-index)))]
    (-> (subvec items 0 new-index)
        (conj (get coll prev-index))
        (into (subvec items new-index)))))

(comment
  (= [0 2 3 4 1 5] (vector-move [0 1 2 3 4 5] 1 4)))

(defn sortable-component []
  (let [items (r/atom (vec (map (fn [i] (str "Item " i)) (range 6))))]
    (fn []
      (r/create-element
        SortableList
        #js {:items @items
             :onSortEnd (fn [event]
                          (swap! items vector-move (.-oldIndex event) (.-newIndex event)))
             :useDragHandle true}))))

(defn main []
  [sortable-component])

(defn start []
  (rdom/render [main] (js/document.getElementById "app")))

(start)
