(ns reagentdemo.news
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.debug :refer-macros [dbg println]]
            [reagentdemo.page :refer [title link page-map]]
            [reagentdemo.common :as common :refer [demo-component]]
            [reagentdemo.news.anyargs :as anyargs]
            [reagentdemo.news.async :as async]
            [reagentdemo.news.undo-demo :as undo-demo]))

(defn main []
  [:div
   [anyargs/main {:summary true}]
   [async/main {:summary true}]
   [undo-demo/main {:summary true}]])
