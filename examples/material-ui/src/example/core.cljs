(ns example.core
  (:require [reagent.core :as r]
            [material-ui :as mui]
            [goog.object :as gobj]
            [reagent.impl.template :as rtpl]))

(def mui-theme-provider (r/adapt-react-class mui/MuiThemeProvider))

(def ^:private input-wrapper
  (r/reactify-component
    (fn [props]
      [:input (-> props
                  (assoc :ref (:inputRef props))
                  (dissoc :inputRef))])))

;; To fix cursor jumping when controlled input value is changed,
;; use wrapper input element created by Reagent instead of
;; letting Material-UI to create input element directly using React.
;; Create-element + convert-props-value is the same as what adapt-react-class does.
(defn text-field [props & children]
  (let [props (-> props
                  (assoc-in [:InputProps :inputComponent] input-wrapper)
                  rtpl/convert-prop-value)]
    (apply r/create-element mui/TextField props children)))

(defonce text-state (r/atom "foobar"))

(defn main []
  [:div
   [:div
    [:strong @text-state]]
   [:button
    {:on-click #(swap! text-state str " foo")}
    "update value property"]
   [:button
    {:on-click #(reset! text-state "")}
    "reset"]
   [text-field
    {:id "example"
     :value @text-state
     :label "Label"
     :placeholder "Placeholder"
     :on-change (fn [e]
                  (reset! text-state (.. e -target -value)))
     :inputRef #(js/console.log "input-ref" %)}]])

(defn start []
  (r/render [main] (js/document.getElementById "app")))

(start)
