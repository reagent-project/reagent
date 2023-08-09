(ns example.core
  (:require [reagent.context :as r.context]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [goog.object :as gobj]))


(defonce my-context (r.context/make-context "my-context" "default"))

(def Provider (.-Provider my-context))
(def Consumer (.-Consumer my-context))

(defn children []
  [:> Consumer {}
   (fn [v]
     (r/as-element [:div "Context: " (pr-str v)]))])

(defn root []
  ;; When using the pure Clojure wrappers, the value is passed as is.
  [:div
   [r.context/provider {:value {:foo :bar} ;; The value here can be anything, does not need to be a map
                        :context my-context}
    [r.context/consumer {:context my-context}
     (fn [{:keys [foo]}]
       [:div ":foo is a keyword: " (pr-str foo)])]

    ;; The `with-context` macro cuts away some boilerplate from the above
    (r.context/with-context [{:keys [foo]} my-context]
      [:div "From the macro: " (pr-str foo)])]]

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
