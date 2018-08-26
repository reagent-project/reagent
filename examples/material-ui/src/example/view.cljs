(ns example.view
  (:require [reagent.core :as r]
            ;; FIXME: Needs some ClojureScript compiler fixes
            ; ["@material-ui/core" :as mui]
            ; ["@material-ui/core/styles" :refer [createMuiTheme]]
            ; ["@material-ui/icons" :as mui-icons]
            ["material-ui" :as mui]
            ["material-ui/styles" :refer [createMuiTheme withStyles]]
            ["material-ui/colors" :as mui-colors]
            ["material-ui-icons" :as mui-icons]
            [re-frame.core :as re :refer [reg-sub]]
            [example.utils.http-fx :refer [<sub >evt]]
            [example.demos.demo-splash :as demo-splash]
            [example.components.sidebar :refer [sidebar-view]]
            [example.routes :as routes :refer [get-main-demo]]
            [goog.object :as gobj]
            [reagent.impl.template :as rtpl]))

;; Example

(def primary-color  (gobj/get (.-blue mui-colors) 100))
(def primary-color-blue "#1976d2")

(def custom-theme
  (createMuiTheme
    #js {:palette #js {:primary #js {:main primary-color-blue}}}))

(defn custom-styles [theme]
  #js {:button #js {:margin (.. theme -spacing -unit)}
       :textField #js {:width 200
                       :marginLeft (.. theme -spacing -unit)
                       :marginRight (.. theme -spacing -unit)}})

(def with-custom-styles (withStyles custom-styles))

(def active-demo demo-splash/demo-splash)

(reg-sub :active-demo
         (fn [db _] (:active-demo db)))

(defn main-view []
  ;; fragment
  (fn []
    (let [active-demo (<sub [:active-demo])
          demo (get-main-demo active-demo)]
            [:div {:style {:display "flex" :flexDirection "row"}}
             [:> mui/CssBaseline]
             [:> mui/MuiThemeProvider
              {:theme custom-theme}
              [sidebar-view]
              [:> mui/Grid
               {:container true
                :direction "row"
                :style {:marginLeft 50}
                :justify "left"}
                [:> (with-custom-styles (r/reactify-component demo))]
               ]]
           ])))
