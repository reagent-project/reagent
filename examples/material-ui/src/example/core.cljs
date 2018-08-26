(ns example.core
  (:require [reagent.core :as r]
            ;; FIXME: Needs some ClojureScript compiler fixes
            ; ["@material-ui/core" :as mui]
            ; ["@material-ui/core/styles" :refer [createMuiTheme]]
            ; ["@material-ui/icons" :as mui-icons]
            ["material-ui" :as mui]
            ["material-ui/styles" :refer [createMuiTheme withStyles]]
            ["material-ui/colors" :as mui-colors]
            ["material-ui-icons" :as mui-icons]
            [re-frame.core :as re]
            [example.view :refer [main-view]]
            [example.events :as events]
            [example.subs :as subs]
            [example.routes :as routes]
            [goog.object :as gobj]
            [reagent.impl.template :as rtpl]))

(defn start []
  (routes/app-routes)
  (re/dispatch-sync [:initialize])
  (r/render [main-view] (js/document.getElementById "app")))

(start)
