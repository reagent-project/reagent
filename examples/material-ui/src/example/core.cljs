(ns example.core
  (:require [reagent.core :as r]
            [material-ui :as mui]
            [goog.object :as gobj]
            [reagent.impl.template :as rtpl]))

(def mui-theme-provider (r/adapt-react-class mui/MuiThemeProvider))
(def menu-item (r/adapt-react-class mui/MenuItem))

(def text-field (rtpl/adapt-input-component mui/TextField))

(defonce text-state (r/atom "foobar"))

(defn main []
  [:form
   {:style {:display "flex"
            :flex-direction "column"
            :flex-wrap "wrap"}}
   [:div
    [:strong @text-state]]

   [:button
    {:type "button"
     :on-click #(swap! text-state str " foo")}
    "update value property"]

   [:button
    {:type "button"
     :on-click #(reset! text-state "")}
    "reset"]

   [text-field
    {:value @text-state
     :label "Text input"
     :placeholder "Placeholder"
     :helper-text "Helper text"
     :on-change (fn [e]
                  (reset! text-state (.. e -target -value)))
     :inputRef #(js/console.log "input-ref" %)}]

   [text-field
    {:value @text-state
     :label "Textarea"
     :placeholder "Placeholder"
     :helper-text "Helper text"
     :on-change (fn [e]
                  (reset! text-state (.. e -target -value)))
     :multiline true}]

   [text-field
    {:value @text-state
     :label "Select"
     :placeholder "Placeholder"
     :helper-text "Helper text"
     :on-change (fn [e]
                  (reset! text-state (.. e -target -value)))
     :select true}
    [menu-item
     {:value 1} "Item 1"]
    [menu-item
     {:value 2} "Item 2"]]])

(defn start []
  (r/render [main] (js/document.getElementById "app")))

(start)
