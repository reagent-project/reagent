(ns shadow-dom-input-demo.core
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [reagent.dom.client :as rdomc]
            ["react-dom/client" :as rdom-client]
            [shadow.cljs.modern :refer [defclass]]))

(defonce shadow-value (r/atom ""))
(defonce normal-value (r/atom ""))
(defonce rendered? (r/atom false))

(defn shadow-input-component []
  [:div {:style {:padding "20px" :background "lightgray"}}
   [:h3 "Shadow DOM Component"]
   [:p "Write something in the box below, then move the cursor (to anything but last position) and write something again. It will move the cursor."]
   [:input {:type "text"
            :placeholder "Shadow DOM input"
            :value @shadow-value
            :on-change (fn [e]
                         (let [v (.. e -target -value)]
                           ;; Simulate async event handling like what an re-frame event handler would do.
                           ;; For some reason just updating the ratom here directly without this doesn't
                           ;; break the cursor like expected.
                           (js/setTimeout (fn []
                                            (reset! shadow-value v))
                                          0)))
            :style {:padding "8px"
                    :border "2px solid #ccc"
                    :border-radius "4px"
                    :font-size "16px"
                    :width "200px"}}]
   [:p "Shadow input value: " @shadow-value]])

(defclass ShadowInput (extends js/HTMLElement)
  (constructor
   [this]
   (super)
   (js/console.log "Rendering shadow input")
   (let [shadow (.attachShadow this #js {:mode "open"})
         container (.createElement js/document "div")
         root (rdomc/create-root container)]
     (.appendChild shadow container)
     (rdomc/render root [shadow-input-component]))))

(defclass ShadowWrapper (extends js/HTMLElement)
  (constructor
    [this]
    (super)
    (let [shadow (.attachShadow this #js {:mode "open"})
          shadow-element (.createElement js/document "shadow-input")]
      (.appendChild shadow shadow-element))))

(defn normal-input []
  [:div
   [:h3 "Normal Component"]
   [:input {:type "text"
            :placeholder "Normal input"
            :value @normal-value
            :on-change #(reset! normal-value (.. % -target -value))
            :style {:padding "8px"
                    :border "2px solid #ccc"
                    :border-radius "4px"
                    :font-size "16px"
                    :width "200px"}}]
   [:p "Normal input value: " @normal-value]])

(defn normal-app []
  [:div {:style {:padding "20px"}}
   [normal-input]])

(defn setup-shadow-component []
  (when-not (.get js/customElements "shadow-input")
    (.define js/customElements "shadow-input" ShadowInput))
  (when-not (.get js/customElements "shadow-wrapper")
    (.define js/customElements "shadow-wrapper" ShadowWrapper))
  (let [shadow-app-div (.getElementById js/document "shadow-app")
        shadow-element (.createElement js/document "shadow-wrapper")]
    (set! (.-innerHTML shadow-app-div) "")
    (.appendChild shadow-app-div shadow-element)))

(defonce root (delay (rdomc/create-root (.getElementById js/document "app"))))

(defn ^:export ^:dev/after-load run []
  (rdomc/render @root [normal-app])
  (setup-shadow-component))
