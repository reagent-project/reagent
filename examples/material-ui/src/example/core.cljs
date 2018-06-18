(ns example.core
  (:require [reagent.core :as r]
            [material-ui :as mui]
            [goog.object :as gobj]
            [reagent.impl.template :as rtpl]))

(def mui-theme-provider (r/adapt-react-class mui/MuiThemeProvider))
(def menu-item (r/adapt-react-class mui/MenuItem))

(defn adapt-input-component
  "Adapts the given custom React input component
  (i.e. it uses :value and :on-change properties)
  for fully controlled use with Reagent.

  Note that this doesn't fix cursor in case where the
  value is transformed, which is handled by Reagent
  for basic :input."
  [component]
  (fn [props & _]
    (r/create-class
      {:display-name "InputWrapper"
       :get-initial-state
       (fn []
         #js {:value (:value props)})
       :should-component-update
       (fn [this old-argv new-args]
         true)
       :component-will-receive-props
       (fn [this [_ props]]
         (when (not= (:value props) (.. this -state -value))
           (.setState this #js {:value (:value props)})))
       :reagent-render
       (fn [props & children]
         (this-as this
           (let [props (-> props
                           (cond-> (:on-change props)
                             (assoc :on-change (fn [e]
                                                 (.setState this #js {:value (.. e -target -value)})
                                                 ((:on-change props) e))))
                           (cond-> (.. this -state -value)
                             (assoc :value (.. this -state -value)))
                           rtpl/convert-prop-value)]
             (apply r/create-element component props (map r/as-element children)))))})))

(def text-field (adapt-input-component mui/TextField))

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
