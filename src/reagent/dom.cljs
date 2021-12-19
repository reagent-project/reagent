(ns reagent.dom
  (:require ["react" :as react]
            ["react-dom" :as react-dom]
            [reagent.impl.util :as util]
            [reagent.impl.template :as tmpl]
            [reagent.impl.batching :as batch]
            [reagent.impl.protocols :as p]
            [reagent.ratom :as ratom]))

(defonce ^:private roots (atom {}))

;; Note: Reagent is storing one root and wrapper component per
;; container DOM node.

(defn- unmount-comp [container]
  (let [[_comp root] (get @roots container)]
    (swap! roots dissoc container)
    (.unmount root)))

(defn- render-comp [comp container callback]
  (binding [util/*always-update* true]
    (let [[comp root] (or (get @roots container)
                          (let [root (react-dom/createRoot container)
                                ;; Wrapper component for useEffect to get
                                ;; callback after component has been mounted.
                                comp (fn render-comp-lifecycle []
                                       (react/useEffect (fn []
                                                          (binding [util/*always-update* false]
                                                            (batch/flush-after-render)
                                                            (if (some? callback)
                                                              (callback))
                                                            js/undefined))
                                                        #js [])
                                       (comp))]
                            (swap! roots assoc container [comp root])
                            [comp root]))]
      (.render root (react/createElement comp)))))

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
   (let [[compiler callback] (cond
                               (map? callback-or-compiler)
                               [(:compiler callback-or-compiler) (:callback callback-or-compiler)]

                               (fn? callback-or-compiler)
                               [tmpl/default-compiler callback-or-compiler]

                               :else
                               [callback-or-compiler nil])
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
  (doseq [[container [comp _root]] @roots]
    (re-render-component comp container))
  (batch/flush-after-render))
