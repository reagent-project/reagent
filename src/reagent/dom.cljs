(ns reagent.dom
  (:require [react-dom :as react-dom]
            [reagent.impl.util :as util]
            [reagent.impl.template :as tmpl]
            [reagent.impl.input :as input]
            [reagent.impl.batching :as batch]
            [reagent.impl.protocols :as p]
            [reagent.ratom :as ratom]))

(defonce ^:private roots (atom {}))

(defn- unmount-comp [container]
  (swap! roots dissoc container)
  (react-dom/unmountComponentAtNode container))

(defn- render-comp [comp container callback]
  (binding [util/*always-update* true]
    (react-dom/render (comp) container
      (fn []
        (binding [util/*always-update* false]
          (swap! roots assoc container comp)
          (batch/flush-after-render)
          (if (some? callback)
            (callback)))))))

(defn- re-render-component [comp container]
  (render-comp comp container nil))

(defn render
  "Render a Reagent component into the DOM. The first argument may be
  either a vector (using Reagent's Hiccup syntax), or a React element.
  The second argument should be a DOM node.

  Optionally takes a callback that is called when the component is in place.

  Returns the mounted component instance."
  ([comp container]
   (render comp container tmpl/default-compiler))
  ([comp container callback-or-compiler]
   (ratom/flush!)
   (let [[compiler callback] (if (fn? callback-or-compiler)
                               [tmpl/default-compiler callback-or-compiler]
                               ;; TODO: Callback option doesn't make sense now that
                               ;; val is compiler object, not map.
                               [callback-or-compiler (:callback callback-or-compiler)])
         f (fn []
             (p/as-element compiler (if (fn? comp) (comp) comp)))]
     (render-comp f container callback))))

(defn unmount-component-at-node
  "Remove a component from the given DOM node."
  [container]
  (unmount-comp container))

(defn dom-node
  "Returns the root DOM node of a mounted component."
  [this]
  (react-dom/findDOMNode this))

(defn force-update-all
  "Force re-rendering of all mounted Reagent components. This is
  probably only useful in a development environment, when you want to
  update components in response to some dynamic changes to code.

  Note that force-update-all may not update root components. This
  happens if a component 'foo' is mounted with `(render [foo])` (since
  functions are passed by value, and not by reference, in
  ClojureScript). To get around this you'll have to introduce a layer
  of indirection, for example by using `(render [#'foo])` instead."
  []
  (ratom/flush!)
  (doseq [[container comp] @roots]
    (re-render-component comp container))
  (batch/flush-after-render))
