
(ns reagent.core
  (:refer-clojure :exclude [partial atom flush])
  (:require [reagent.impl.template :as tmpl]
            [reagent.impl.component :as comp]
            [reagent.impl.util :as util]
            [reagent.impl.batching :as batch]
            [reagent.ratom :as ratom]
            [reagent.debug :refer-macros [dbg prn]]
            [reagent.interop :refer-macros [.' .!]]))

(def is-client util/is-client)

(defn as-component
  "Turns a vector of Hiccup syntax into a React component. Returns form unchanged if it is not a vector."
  [form]
  (tmpl/as-component form))

(defn render-component
  "Render a Reagent component into the DOM. The first argument may be either a
vector (using Reagent's Hiccup syntax), or a React component. The second argument should be a DOM node.

Optionally takes a callback that is called when the component is in place.

Returns the mounted component instance."
  ([comp container]
     (render-component comp container nil))
  ([comp container callback]
   (let [f (fn []
             (as-component (if (fn? comp) (comp) comp)))]
     (util/render-component f container callback))))

(defn unmount-component-at-node
  "Remove a component from the given DOM node."
  [container]
  (util/unmount-component-at-node container))

(defn render-component-to-string
  "Turns a component into an HTML string."
  ([component]
     (.' js/React renderComponentToString (as-component component))))

(defn ^:export force-update-all []
  (util/force-update-all))

(defn create-class
  "Create a component, React style. Should be called with a map,
looking like this:
{:get-initial-state (fn [this])
:component-will-receive-props (fn [this new-argv])
:should-component-update (fn [this old-argv new-argv])
:component-will-mount (fn [this])
:component-did-mount (fn [this])
:component-will-update (fn [this new-argv])
:component-did-update (fn [this old-argv])
:component-will-unmount (fn [this])
:render (fn [this])}

Everything is optional, except :render.
"
  [spec]
  (tmpl/create-class spec))


(defn current-component
  "Returns the current React component (a.k.a this) in a component
  function."
  []
  comp/*current-component*)


(defn state
  "Returns the state of a component, as set with replace-state or set-state."
  [this]
  (assert (util/reagent-component? this))
  ;; TODO: Warn if top-level component
  (comp/state this))

(defn replace-state
  "Set state of a component."
  [this new-state]
  (assert (util/reagent-component? this))
  (assert (or (nil? new-state) (map? new-state)))
  (comp/replace-state this new-state))

(defn set-state
  "Merge component state with new-state."
  [this new-state]
  (assert (util/reagent-component? this))
  (assert (or (nil? new-state) (map? new-state)))
  (comp/set-state this new-state))


(defn props
  "Returns the props passed to a component."
  [this]
  (assert (util/reagent-component? this))
  (util/get-props this))

(defn children
  "Returns the children passed to a component."
  [this]
  (assert (util/reagent-component? this))
  (util/get-children this))

(defn argv
  "Returns the entire Hiccup form passed to the component."
  [this]
  (assert (util/reagent-component? this))
  (util/get-argv this))

(defn dom-node
  "Returns the root DOM node of a mounted component."
  [this]
  (.' this getDOMNode))

(defn merge-props
  "Utility function that merges two maps, handling :class and :style
specially, like React's transferPropsTo."
  [defaults props]
  (util/merge-props defaults props))

(defn flush
  "Render dirty components immediately to the DOM.

Note that this may not work in event handlers, since React.js does
batching of updates there."
  []
  (batch/flush))



;; Ratom

(defn atom
  "Like clojure.core/atom, except that it keeps track of derefs.
Reagent components that derefs one of these are automatically
re-rendered."
  ([x] (ratom/atom x))
  ([x & rest] (apply ratom/atom x rest)))

;; RCursor

(defn cursor
  "Provide a cursor into a Reagent atom.

Behaves like a Reagent atom but focuses updates and derefs to
the specified path within the wrapped Reagent atom. e.g.,
  (let [c (cursor [:nested :content] ra)]
    ... @c ;; equivalent to (get-in @ra [:nested :content])
    ... (reset! c 42) ;; equivalent to (swap! ra assoc-in [:nested :content] 42)
    ... (swap! c inc) ;; equivalence to (swap! ra update-in [:nested :content] inc)
    )"
  ([path] (fn [ra] (cursor path ra)))
  ([path ra] (ratom/cursor path ra)))

;; Utilities

(defn next-tick
  "Run f using requestAnimationFrame or equivalent."
  [f]
  (batch/next-tick f))

(defn partial
  "Works just like clojure.core/partial, except that it is an IFn, and
the result can be compared with ="
  [f & args]
  (util/partial-ifn. f args nil))

