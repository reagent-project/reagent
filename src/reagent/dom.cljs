(ns reagent.dom
  (:require [cljsjs.react.dom]
            [reagent.impl.util :as util]
            [reagent.impl.template :as tmpl]
            [reagent.debug :refer-macros [dbg]]
            [reagent.interop :refer-macros [.' .!]]))

(defonce react-dom (or (and (exists? js/ReactDOM)
                            js/ReactDOM)
                       (and (exists? js/require)
                            (js/require "react-dom"))))
(assert react-dom)

(defonce ^:private roots (atom {}))

(defn unmount-comp [container]
  (swap! roots dissoc container)
  (.' react-dom unmountComponentAtNode container))

(defn- clear-container [node]
  ;; If render throws, React may get confused, and throw on
  ;; unmount as well, so try to force React to start over.
  (try (unmount-comp node)
       (catch :default e))
  (some-> node
          (.! :innerHTML "")))

(defn- render-comp [comp container callback]
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

(defn- re-render-component [comp container]
  (render-comp comp container nil))

(defn render
  "Render a Reagent component into the DOM. The first argument may be
either a vector (using Reagent's Hiccup syntax), or a React element. The second argument should be a DOM node.

Optionally takes a callback that is called when the component is in place.

Returns the mounted component instance."
  ([comp container]
   (render comp container nil))
  ([comp container callback]
   (let [f (fn []
             (tmpl/as-element (if (fn? comp) (comp) comp)))]
     (render-comp f container callback))))

(defn unmount-component-at-node [container]
  (unmount-comp container))

(defn dom-node
  "Returns the root DOM node of a mounted component."
  [this]
  (.' react-dom findDOMNode this))

(set! tmpl/find-dom-node dom-node)

(defn force-update-all []
  (doseq [v (vals @roots)]
    (apply re-render-component v))
  "Updated")
