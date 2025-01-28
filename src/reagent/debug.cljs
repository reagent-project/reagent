(ns reagent.debug
  (:require-macros [reagent.debug]))

(def ^:const has-console (exists? js/console))

(def ^boolean tracking false)

(defonce warnings (atom nil))

(defonce track-console
  (let [o #js {}]
    (set! (.-warn o)
          (fn [& args]
            (swap! warnings update-in [:warn] conj (apply str args))))
    (set! (.-error o)
          (fn [& args]
            (swap! warnings update-in [:error] conj (apply str args))))
    o))

(defn track-warnings [f]
  (set! tracking true)
  (reset! warnings nil)
  (f)
  (let [warns @warnings]
    (reset! warnings nil)
    (set! tracking false)
    warns))
