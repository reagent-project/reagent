(ns example.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [react :as react]
            [goog.object :as gobj]))


(defonce my-context (react/createContext "default"))

(def Provider (.-Provider my-context))
(def Consumer (.-Consumer my-context))

(defn children []
  [:> Consumer {}
   (fn [v]
     (r/as-element [:div "Context: " (pr-str v)]))])

(defn root []
  ;; Provider takes props with single property, value
  ;; :< or adapt-react-class converts the Cljs properties
  ;; map to JS object for the Provider component.
  [:div
   [:> Provider {:value "bar"}
    [children]]

   ;; :> and adapt-react-class convert the props
   ;; recursively to JS objects, so this might not be
   ;; what you want.
   [:> Provider {:value {:foo "bar"}}
    [children]]

   ;; To yourself control how the props are handled,
   ;; use create-element directly.
   ;; Properties map needs to be JS object here, but now the
   ;; value is used as is, Cljs map.
   ;; Note that you need to convert children from Reagent markup
   ;; to React elements yourself.
   (r/create-element Provider
     #js {:value {:foo "bar"}}
     (r/as-element [children]))])

(defn start []
  (rdom/render [root] (js/document.getElementById "app")))

(start)
