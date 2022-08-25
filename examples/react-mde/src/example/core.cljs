(ns example.core
  "This example shows how to use the `react-mde` library, which provides a markdown editor as
  a React component.
  The integration is not straightforward because we need to provide a custom `textarea`
  component to `ReactMde` so we can have the cursor positining fixes needed by reagent. BUT
  we must make sure that `ReactMde` sees a ref to the true `textarea` component, and not reagent's
  wrapper."
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            ["react-mde$default" :as ReactMde]
            ["react-markdown" :as ReactMarkdown]
            [react :as react]
            ;; FIXME: Internal impl namespace should not be used
            [reagent.impl.template :as rtpl]))


;;
;; Constants and helpers
;;
(def common-style {:width "700px" :margin "60px auto"})

(defn render-markdown
  "Uses ReactMarkdown to render the markdown. Returns a `Promise` as expected by `ReactMde`."
  [source]
  (js/Promise.
   (fn [resolve _]
     (resolve
      (r/as-element
       [:> ReactMarkdown {:source source}])))))


;;
;; Two broken examples that won't work properly.
;;
(defn without-any-fix
  "Renders a ReactMde component without any fix for textarea component."
  []
  (let [state (r/atom {:value "Initial Value!" :tab "write"})]
    (fn []
      [:div {:style common-style}
       [:h3 "Without any fix"]
       [:i "Cursor goes to the end at every keystroke."]
       [:> ReactMde {:value (:value @state)
                     :on-change #(swap! state assoc :value %)
                     :selected-tab (:tab @state)
                     :on-tab-change #(swap! state assoc :tab %)
                     :generate-markdown-preview #(render-markdown %)}]
       [:div "Value: " (:value @state)]])))

(defn with-custom-textarea-but-no-forward-ref
  "Renders a ReactMde component with a custom textarea, but without `forwardRef`"
  []
  (let [state (r/atom {:value "Initial Value!" :tab "write"})
        textarea (r/create-class {:reagent-render (fn [props] [:textarea props])})]
    (fn []
      [:div {:style common-style}
       [:h3 "With custom textarea but no forwardRef"]
       [:i "Cursor behaves fine, but the functionalities from the toolbar are broken."]
       [:> ReactMde {:value (:value @state)
                     :on-change #(swap! state assoc :value %)
                     :selected-tab (:tab @state)
                     :on-tab-change #(swap! state assoc :tab %)
                     :generate-markdown-preview #(render-markdown %)
                     :text-area-component textarea}]
       [:div "Value: " (:value @state)]])))


;;
;; Real working example
;;
(def textarea-component
  "This is a trick needed so that the `textarea` component used by the `ReactMde` works.
  1. Use reagent's custom `textarea` component, instead of a plain `textarea`.
  2. Use ForwardRef to ensure that the ref seen by ReactMde points to the plain `textarea`
     instead of reagent wrapper."
  (react/forwardRef
   (fn textarea [props ref]
     (let [props (assoc (js->clj props) :ref ref)]
       (r/as-element [:textarea props])))))


(defn react-mde
  "Wrapper around ReactMde using our custom textarea-component"
  [props]
  ;; FIXME: Internal fn should not be used
  (let [props (rtpl/convert-prop-value (assoc props :text-area-component textarea-component))]
    (r/create-element ReactMde props)))


(defn working-example []
  (let [state (r/atom {:value "Initial Value!" :tab "write"})]
    (fn []
      [:div {:style common-style}
       [:h3 "Working example!"]
       [react-mde {:value (:value @state)
                   :on-change #(swap! state assoc :value %)
                   :selected-tab (:tab @state)
                   :on-tab-change #(swap! state assoc :tab %)
                   :generate-markdown-preview #(render-markdown %)}]
       [:div "Value: " (:value @state)]])))


(defn main []
  [:<>
   [without-any-fix]
   [with-custom-textarea-but-no-forward-ref]
   [working-example]])

(defn start []
  (rdom/render [main] (js/document.getElementById "app")))

(start)
