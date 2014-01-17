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


