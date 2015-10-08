(ns reagent.dom
  (:require [cljsjs.react.dom]
            [reagent.impl.util :as util]
            [reagent.debug :refer-macros [dbg]]
            [reagent.interop :refer-macros [.' .!]]))

(def react-dom js/ReactDOM)
(assert react-dom)

(defonce roots (atom {}))

(defn clear-container [node]
  ;; If render throws, React may get confused, and throw on
  ;; unmount as well, so try to force React to start over.
  (some-> node
          (.! :innerHTML "")))

(defn render-component [comp container callback]
  (let [rendered (volatile! nil)]
    (try
      (binding [util/*always-update* true]
        (->> (.' react-dom render (comp) container
                 (fn []
                   (binding [util/*always-update* false]
                     (swap! roots assoc container [comp container])
                     (if (some? callback)
                       (callback)))))
             (vreset! rendered)))
      (finally
        (when-not @rendered
          (clear-container container))))))

(defn re-render-component [comp container]
  (render-component comp container nil))

(defn unmount-component-at-node [container]
  (swap! roots dissoc container)
  (.' react-dom unmountComponentAtNode container))

(defn force-update-all []
  (doseq [v (vals @roots)]
    (apply re-render-component v))
  "Updated")

(defn dom-node
  "Returns the root DOM node of a mounted component."
  [this]
  (.' react-dom findDOMNode this))
