(ns example.core
  (:require [reagent.core :as r]
            [material-ui :as mui]
            [goog.object :as gobj]
            [reagent.impl.template :as rtpl]))

(def mui-theme-provider (r/adapt-react-class mui/MuiThemeProvider))
(def menu-item (r/adapt-react-class mui/MenuItem))

(def ^:private input-component
  (r/reactify-component
    (fn [props]
      [:input (-> props
                  (assoc :ref (:inputRef props))
                  (dissoc :inputRef))])))

(def ^:private textarea-component
  (r/reactify-component
    (fn [props]
      [:textarea (-> props
                     (assoc :ref (:inputRef props))
                     (dissoc :inputRef))])))

;; To fix cursor jumping when controlled input value is changed,
;; use wrapper input element created by Reagent instead of
;; letting Material-UI to create input element directly using React.
;; Create-element + convert-props-value is the same as what adapt-react-class does.
(defn text-field [props & children]
  (let [props (-> props
                  (assoc-in [:InputProps :inputComponent] (cond
                                                            (and (:multiline props) (:rows props) (not (:maxRows props)))
                                                            textarea-component

                                                            ;; FIXME: Autosize multiline field is broken.
                                                            (:multiline props)
                                                            nil

                                                            ;; Select doesn't require cursor fix so default can be used.
                                                            (not (:select props))
                                                            nil

                                                            :else
                                                            input-component))
                  rtpl/convert-prop-value)]
    (apply r/create-element mui/TextField props (map r/as-element children))))

(defonce text-state (r/atom "foobar"))

(defn main []
  [:form
   {:style {:display "flex"
            :flex-direction "column"
            :flex-wrap "wrap"}}
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
     :label "Text input"
     :placeholder "Placeholder"
     :helper-text "Helper text"
     :on-change (fn [e]
                  (reset! text-state (.. e -target -value)))
     :inputRef #(js/console.log "input-ref" %)}]

   [text-field
    {:id "example"
     :value @text-state
     :label "Textarea"
     :placeholder "Placeholder"
     :helper-text "Helper text"
     :on-change (fn [e]
                  (reset! text-state (.. e -target -value)))
     :multiline true
     ;; TODO: Autosize textarea is broken.
     :rows 10}]

   [text-field
    {:id "example"
     :value @text-state
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
