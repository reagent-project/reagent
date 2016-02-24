(ns reagent.dom.server
  (:require [cljsjs.react.dom.server]
            [reagent.impl.util :as util]
            [reagent.impl.template :as tmpl]
            [reagent.interop :refer-macros [$ $!]]))

(def ^:private load-error nil)

(defn- fail [e]
  (set! load-error e)
  nil)

(defonce server (or (when (exists? js/ReactDOMServer)
                      js/ReactDOMServer)
                    (try
                      (if (exists? js/require)
                        (or (js/require "react-dom/server")
                            (fail (js/Error.
                                   "require('react-dom/server') failed")))
                        (fail (js/Error. "js/ReactDOMServer is missing")))
                      (catch :default e
                        (fail e)))))

(defn- module []
  (if (some? server)
    server
    (throw load-error)))

(defn render-to-string
  "Turns a component into an HTML string."
  [component]
  (binding [util/*non-reactive* true]
    ($ (module) renderToString (tmpl/as-element component))))

(defn render-to-static-markup
  "Turns a component into an HTML string, without data-react-id attributes, etc."
  [component]
  (binding [util/*non-reactive* true]
    ($ (module) renderToStaticMarkup (tmpl/as-element component))))
