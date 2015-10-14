(ns reagent.dom.server
  (:require [cljsjs.react.dom]
            [reagent.impl.util :as util]
            [reagent.impl.template :as tmpl]
            [reagent.interop :refer-macros [.' .!]]))

;; TODO: Where the hell is ReactDOMServer?
(defonce react-dom-server (or (and (exists? js/require)
                                   (js/require "react-dom/server"))
                              util/react))
(assert react-dom-server)

(defn render-to-string
  "Turns a component into an HTML string."
  ([component]
   (binding [util/*non-reactive* true]
     (.' react-dom-server renderToString (tmpl/as-element component)))))

(defn render-to-static-markup
  "Turns a component into an HTML string, without data-react-id attributes, etc."
  ([component]
   (binding [util/*non-reactive* true]
     (.' react-dom-server renderToStaticMarkup (tmpl/as-element component)))))
