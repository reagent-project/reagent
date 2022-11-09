(ns reagent.dom
  (:require ["react" :as react]
            ["react-dom" :as react-dom]
            [reagent.impl.util :as util]
            [reagent.impl.template :as tmpl]
            [reagent.impl.batching :as batch]
            [reagent.impl.protocols :as p]
            [reagent.ratom :as ratom]
            [goog.object :as gobj]))

(defonce ^:private roots (atom {}))

(defn- unmount-comp [container]
  (let [root (get @roots container)]
    (swap! roots dissoc container)
    (.unmount root)))

(defn- reagent-root [^js js-props]
  (let [comp (gobj/get js-props "comp")
        callback (gobj/get js-props "callback")]
    (react/useEffect (fn []
                       (binding [util/*always-update* false]
                         (batch/flush-after-render)
                         (when (some? callback)
                           (callback))
                         js/undefined)))
    (binding [util/*always-update* true]
      (comp))))

(defn- render-comp [comp container callback]
  (let [root (reify Object
               (unmount [_this]
                 (react-dom/unmountComponentAtNode container))
               (render [_this]
                 (react-dom/render
                   (react/createElement reagent-root #js {:callback callback
                                                          :comp comp})
                   container)))]
    (swap! roots assoc container root)
    (.render root)))

(defn render
  "Render a Reagent component into the DOM. The first argument may be
  either a vector (using Reagent's Hiccup syntax), or a React element.
  The second argument should be a DOM node.

  Optionally takes a callback that is called when the component is in place.

  Returns the mounted component instance."
  ([comp container]
   (render comp container tmpl/*current-default-compiler*))
  ([comp container callback-or-compiler]
   (ratom/flush!)
   (let [[compiler callback] (cond
                               (map? callback-or-compiler)
                               [(:compiler callback-or-compiler) (:callback callback-or-compiler)]

                               (fn? callback-or-compiler)
                               [tmpl/*current-default-compiler* callback-or-compiler]

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
  {:deprecated "1.2.0"}
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
  {:deprecated "1.2.0"}
  []
  (ratom/flush!)
  (doseq [[container root] @roots]
    (.render root))
  (batch/flush-after-render))
