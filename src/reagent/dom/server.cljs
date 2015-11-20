(ns reagent.dom.server
  (:require [cljsjs.react.dom.server]
            [reagent.impl.util :as util]
            [reagent.impl.template :as tmpl]
            [reagent.interop :refer-macros [$ $!]]))

(defonce ^:private react-server nil)

(defn- server []
  (if-some [r react-server]
    r
    (do
      (set! react-server
            (or (and (exists? js/ReactDOMServer)
                     js/ReactDOMServer)
                (and (exists? js/require)
                     (js/require "react-dom/server"))))
      (assert react-server "Could not find ReactDOMServer")
      react-server)))

(defn render-to-string
  "Turns a component into an HTML string."
  [component]
  (binding [util/*non-reactive* true]
    ($ (server) renderToString (tmpl/as-element component))))

(defn render-to-static-markup
  "Turns a component into an HTML string, without data-react-id attributes, etc."
  [component]
  (binding [util/*non-reactive* true]
    ($ (server) renderToStaticMarkup (tmpl/as-element component))))
