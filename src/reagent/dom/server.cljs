(ns reagent.dom.server
  (:require ["react-dom/server" :as dom-server]
            [reagent.impl.util :as util]
            [reagent.impl.template :as tmpl]
            [reagent.impl.protocols :as p]
            [reagent.ratom :as ratom]))

(defn render-to-string
  "Turns a component into an HTML string."
  ([component]
   (render-to-string component tmpl/default-compiler))
  ([component compiler]
   (ratom/flush!)
   (binding [util/*non-reactive* true]
     (dom-server/renderToString (p/as-element compiler component)))))

(defn render-to-static-markup
  "Turns a component into an HTML string, without data-react-id attributes, etc."
  ([component]
   (render-to-static-markup component tmpl/default-compiler))
  ([component compiler]
   (ratom/flush!)
   (binding [util/*non-reactive* true]
     (dom-server/renderToStaticMarkup (p/as-element compiler component)))))
