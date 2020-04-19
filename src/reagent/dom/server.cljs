(ns reagent.dom.server
  (:require ["react-dom/server" :as dom-server]
            [reagent.impl.util :as util]
            [reagent.impl.template :as tmpl]
            [reagent.ratom :as ratom]))

(defn render-to-string
  "Turns a component into an HTML string."
  ([component]
   (render-to-string component nil))
  ([component opts]
   (ratom/flush!)
   (binding [util/*non-reactive* true]
     (dom-server/renderToString (tmpl/as-element component opts)))))

(defn render-to-static-markup
  "Turns a component into an HTML string, without data-react-id attributes, etc."
  ([component]
   (render-to-static-markup component nil))
  ([component opts]
   (ratom/flush!)
  (binding [util/*non-reactive* true]
    (dom-server/renderToStaticMarkup (tmpl/as-element component opts)))))
