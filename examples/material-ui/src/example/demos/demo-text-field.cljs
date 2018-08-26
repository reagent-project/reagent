(ns example.demos.demo-text-field
  (:require [reagent.core :as r]
            ["material-ui" :as mui]
            ["material-ui-icons" :as mui-icons]
            [reagent.impl.template :as rtpl])
  )

  ;; TextField cursor fix:

  (defonce text-state (r/atom "foobar"))

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
                                                              (:select props)
                                                              nil

                                                              :else
                                                              input-component))
                    rtpl/convert-prop-value)]
      (apply r/create-element mui/TextField props (map r/as-element children))))


(defn demo-text-field [{:keys [classes] :as props}]
 (let [component-state (r/atom {:selected 0})]
    (fn []
      (let [current-select (get @component-state :selected)]
      [:div {:style {:display "block"
                     :position "relative"
                     }}
        [:> mui/Grid
         {:container true
          :direction "column"}
         [:h2 "Text Field"]  
         [text-field
          {:value @text-state
           :label "Text input"
           :placeholder "Placeholder"
           :helper-text "Helper text"
           :class (.-textField classes)
           :on-change (fn [e]
                        (reset! text-state (.. e -target -value)))
           :inputRef #(js/console.log "input-ref" %)}]

         [text-field
          {:value @text-state
           :label "Textarea"
           :placeholder "Placeholder"
           :helper-text "Helper text"
           :class (.-textField classes)
           :on-change (fn [e]
                        (reset! text-state (.. e -target -value)))
           :multiline true
           ;; TODO: Autosize textarea is broken.
           :rows 10}]

         [text-field
          {:value @text-state
           :label "Select"
           :placeholder "Placeholder"
           :helper-text "Helper text"
           :class (.-textField classes)
           :on-change (fn [e]
                        (reset! text-state (.. e -target -value)))
           :select true}
          [:> mui/MenuItem
           {:value 1}
           "Item 1"]
          ;; Same as previous, alternative to adapt-react-class
          [:> mui/MenuItem
           {:value 2}
           "Item 2"]]]
      ]
    ))))
