(ns example.demos.demo-splash
  (:require [reagent.core :as r]
            ["material-ui" :as mui]
            ["material-ui-icons" :as mui-icons]
            [example.utils.http-fx :refer [set-location]]
            [reagent.impl.template :as rtpl])
  )



(defn demo-splash [{:keys [classes] :as props}]
 (let [component-state (r/atom {:selected 0})]
    (fn []
      (let [current-select (get @component-state :selected)]
      [:div {:style {:display "flex"
                     :flexDirection "column"
                     :position "relative"
                     :margin 50
                     :alignItems "center"
                     }}
        [:div {:style {:margin 10}}
          [:img {:src "/images/material-ui-logo.svg" :width 200}]]
        [:h1 {:style {:fontFamily "Helvetica" :color "#666"}} "MATERIAL-UI"]
        [:h2 {:style {:fontSize 20 :margin 0 :padding 0 :fontFamily "Helvetica" :color "#666"}} "React components that implement Google's Material Design"]
        [:h2 {:style {:fontSize 20 :margin 0 :padding 0 :fontFamily "Helvetica" :color "#666"}} "with ClojureScript using Reagent & Re-Frame"]
        [:div {:style {:margin 20}}
          [:> mui/Button
           {:variant "contained"
            :color "primary"
            :class (.-button classes)
            :on-click (fn [ev] (set-location "/#/demos/button"))}
           "Get Started"
           ]
         ]
      ]
    ))))
