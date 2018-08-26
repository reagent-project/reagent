(ns example.demos.demo-template
  (:require [reagent.core :as r]
            ["material-ui" :as mui]
            ["material-ui-icons" :as mui-icons]
            [reagent.impl.template :as rtpl])
  )

(defn demo-template [{:keys [classes] :as props}]
 (let [component-state (r/atom {:selected 0})]
    (fn []
      (let [current-select (get @component-state :selected)]
      [:div {:style {:display "block"
                     :position "relative"
                     }}
        [:h2 "Template"]  
      ]
    ))))
