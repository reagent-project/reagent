(ns reagentdemo.page
  (:require [reagent.core :as reagent :refer [atom partial]]
            [reagent.debug :refer-macros [dbg]]
            [clojure.string :as string]
            [goog.events :as events])
  (:import [goog History]
           [goog.history Html5History]
           [goog.history EventType]))

(def page (atom ""))

(defn create-history []
  (when reagent/is-client
    (let [proto (-> js/window .-location .-protocol)]
      (if (and (.isSupported Html5History)
               (case proto "http:" true "https:" true false))
        (doto (Html5History.)
          (.setUseFragment false))
        (History.)))))

(defn setup-history []
  (when-let [h (create-history)]
    (events/listen h EventType/NAVIGATE
                   (fn [e] (reset! page (.-token e))))
    (add-watch page ::history (fn [_ _ oldp newp]
                                (.setToken h newp)))
    (.setEnabled h true)
    h))

(def history (setup-history))

(def title-atom (atom ""))

(def page-map (atom nil))

(def reverse-page-map (atom nil))

(add-watch page-map ::page-map-watch
           (fn [_ _ _ new-map]
             (reset! reverse-page-map
                     (into {} (for [[k v] new-map]
                                [v k])))))

(defn prefix [href]
  (let [depth (-> #"/" (re-seq @page) count)]
    (str (->> "../" (repeat depth) (apply str)) href)))

(defn link [props children]
  (let [rpm @reverse-page-map
        href (-> props :href rpm)]
    (assert (string? href))
    (apply vector
           :a (assoc props
                :href (prefix href)
                :on-click (if history
                            (fn [e]
                              (.preventDefault e)
                              (reset! page href))
                            identity))
           children)))

(defn title [props children]
  (let [name (first children)]
    (if reagent/is-client
      (let [title (aget (.getElementsByTagName js/document "title") 0)]
        (set! (.-innerHTML title) name)))
    (reset! title-atom name)
    [:div]))
