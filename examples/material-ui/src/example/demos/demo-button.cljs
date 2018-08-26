(ns example.demos.demo-button
  (:require [reagent.core :as r]
            ["material-ui" :as mui]
            ["material-ui-icons" :as mui-icons])
  )


(defonce text-state (r/atom "foobar"))

(defn demo-button [{:keys [classes] :as props}]
 (let [component-state (r/atom {:selected 0})]
    (fn []
      (let [current-select (get @component-state :selected)]
      [:div {:style {:display "block"
                     :position "relative"
                     }}
      [:h2 "Button"]
      [:code
        "would be nice to have a code example here - "
        [:a {:href "http://blog.klipse.tech/clojure/2016/03/17/klipse.html"} "perhaps using klipse"]
      ]
      [:> mui/Toolbar
       {:disable-gutters true}
       [:> mui/Button
        {:variant "contained"
         :color "primary"
         :class (.-button classes)
         :on-click #(swap! text-state str " foo")}
        "Update value property"
        [:> mui-icons/AddBox]]

       [:> mui/Button
        {:variant "outlined"
         :color "secondary"
         :class (.-button classes)
         :on-click #(reset! text-state "")}
        "Reset"
        [:> mui-icons/Clear]]]]
    ))))
