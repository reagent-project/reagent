(ns example.core
  (:require ["@mui/material/Autocomplete$default" :as Autocomplete]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/CssBaseline$default" :as CssBaseline]
            ["@mui/material/Grid$default" :as Grid]
            ["@mui/material/TextField$default" :as TextField]
            ["@mui/material/Toolbar$default" :as Toolbar]
            ["@mui/material/MenuItem$default" :as MenuItem]
            ["@mui/material/colors" :as mui-colors]
            ["@mui/material/styles" :refer [createTheme ThemeProvider]]
            ["react" :as react]
            ["@mui/icons-material" :as mui-icons]
            [goog.object :as gobj] ;; FIXME: Internal impl namespace should not be used
            [reagent.core :as r]
            [reagent.dom.client :as rdomc]
            [reagent.impl.template :as rtpl]))

(set! *warn-on-infer* true)

(defn event-value
  [^js/Event e]
  (let [^js/HTMLInputElement el (.-target e)]
    (.-value el)))

;; TextField cursor fix:

;; For some reason the new MUI doesn't pass ref in the props,
;; but we can get it using forwardRef?
;; This is someone incovenient as we need to convert props to Cljs
;; but reactify-component would also do that.
(def ^:private input-component
  (react/forwardRef
    (fn [props ref]
      (r/as-element
        [:input (-> (js->clj props :keywordize-keys true)
                    (assoc :ref ref))]))))

(def ^:private textarea-component
  (react/forwardRef
    (fn [props ref]
      (r/as-element
        [:textarea (-> (js->clj props :keywordize-keys true)
                       (assoc :ref ref))]))))

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
                  ;; FIXME: Internal fn should not be used
                  ;; clj->js is not enough as prop on-change -> onChange, class -> classNames etc should be handled
                  rtpl/convert-prop-value)]
    (apply r/create-element TextField props (map r/as-element children))))

;; Example

(def custom-theme
  (createTheme
    #js {:palette #js {:primary #js {:main (gobj/get mui-colors/red 100)}}}))

(defonce text-state (r/atom "foobar"))
(defonce select-state (r/atom ""))

(defn autocomplete-example []
  [:> Grid
   {:item true}
   [:> Autocomplete {:options ["foo" "bar" "foobar"]
                     :style {:width 300}
                     ;; Note that the function parameter is a JS Object!
                     ;; Autocomplete expects the renderInput value to be function
                     ;; returning React elements, not a component!
                     ;; So reactify-component won't work here.
                     :render-input (fn [^js params]
                                     ;; Don't call js->clj because that would recursively
                                     ;; convert all JS objects (e.g. React ref objects)
                                     ;; to Cljs maps, which breaks them, even when converted back to JS.
                                     ;; Best thing is to use r/create-element and
                                     ;; pass the JS params to it.
                                     ;; If necessary, use JS interop to modify params.
                                     (set! (.-variant params) "outlined")
                                     (set! (.-label params) "Autocomplete")
                                     (r/create-element TextField params))}]])

;; Props in cljs but classes in JS object
(defn form []
  [:> Grid
   {:container true
    :direction "column"
    :spacing 2
    :sx {".MuiButton-root" {:m 1}
         ".MuiTextField-root" {:width 200
                               :mx 1}}}

   [:> Grid {:item true}
    [:> Toolbar
     {:disable-gutters true}
     [:> Button
      {:variant "contained"
       :color "primary"
       :on-click #(swap! text-state str " foo")}
      "Update value property"
      [:> mui-icons/AddBox]]

     [:> Button
      {:variant "outlined"
       :color "secondary"
       :on-click #(reset! text-state "")}
      "Reset"
      [:> mui-icons/Clear]]]]

   [:> Grid {:item true}
    [text-field
     {:value @text-state
      :label "Text input"
      :placeholder "Placeholder"
      :helper-text "Helper text"
      :on-change (fn [e]
                   (reset! text-state (event-value e)))
      :inputRef (fn [e]
                  (js/console.log "input-ref" e))}]]

   [:> Grid {:item true}
    [text-field
     {:value @text-state
      :label "Textarea"
      :placeholder "Placeholder"
      :helper-text "Helper text"
      :on-change (fn [e]
                   (reset! text-state (event-value e)))
      :multiline true
      ;; TODO: Autosize textarea is broken.
      :rows 10}]]

   [:> Grid {:item true}
    [text-field
     {:value @select-state
      :label "Select"
      :placeholder "Placeholder"
      :helper-text "Helper text"
      :on-change (fn [e]
                   (reset! select-state (event-value e)))
      :select true}
     [:> MenuItem
      {:value 1}
      "Item 1"]
     ;; Same as previous, alternative to adapt-react-class
     [:> MenuItem
      {:value 2}
      "Item 2"]]]

   [:> Grid {:item true}
    [:> Grid
     {:container true
      :direction "row"
      :spacing 4}

     ;; For properties that require React Node as parameter,
     ;; either use r/as-element to convert Reagent hiccup forms into React elements,
     ;; or use r/create-element to directly instantiate element from React class (i.e. non-adapted React component).
     [:> Grid {:item true}
      [:> Chip
       {:icon (r/as-element [:> mui-icons/Face])
        :label "Icon element example, r/as-element"}]]

     [:> Grid {:item true}
      [:> Chip
       {:icon (r/create-element mui-icons/Face)
        :label "Icon element example, r/create-element"}]]]]

   [:> Grid {:item true}
    [:> Grid
     {:container true
      :direction "row"
      :spacing 4}
     [autocomplete-example]]]])

(defn main []
  ;; fragment
  [:<>
   [:> CssBaseline]
   [:> ThemeProvider
    {:theme custom-theme}
    [:> Grid
     {:container true
      :direction "row"
      :justify "center"}
     [:> Grid
      {:item true
       :xs 6}
      [form]]]]])

(defonce root (delay (rdomc/create-root (.getElementById js/document "app"))))

(defn start []
  (rdomc/render @root [main]))

(start)
