
(ns reagent.core
  (:refer-clojure :exclude [partial atom flush])
  (:require-macros [reagent.debug :refer [dbg prn]])
  (:require [reagent.impl.template :as tmpl]
            [reagent.impl.component :as comp]
            [reagent.impl.util :as util]
            [reagent.ratom :as ratom]))

(def React tmpl/React)

(def is-client tmpl/isClient)

(defn render-component
  "Render a Reagent component into the DOM. The first argument may be either a
vector (using Reagent's Hiccup syntax), or a React component. The second argument should be a DOM node.

Optionally takes a callback that is called when the component is in place.

Returns the mounted component instance."
  ([comp container]
     (render-component comp container nil))
  ([comp container callback]
     (.renderComponent React (tmpl/as-component comp) container callback)))

(defn unmount-component-at-node
  "Remove a component from the given DOM node."
  [container]
  (.unmountComponentAtNode React container))

(defn render-component-to-string
  "Turns a component into an HTML string."
  ([component]
     (let [res (clojure.core/atom nil)]
       (render-component-to-string component #(reset! res %))
       @res))
  ([component callback]
     (.renderComponentToString React (tmpl/as-component component) callback)))

(defn create-class
  "Create a component, React style. Should be called with a map,
looking like this:
{:get-initial-state (fn [this])
:component-will-receive-props (fn [this new-props])
:should-component-update (fn [this old-props new-props old-children new-children])
:component-will-mount (fn [this])
:component-did-mount (fn [this])
:component-will-update (fn [this new-props new-children])
:component-did-update (fn [this old-props old-children])
:component-will-unmount (fn [this])
:render (fn [props children this])}

Everything is optional, except :render.
"
  [spec]
  (comp/create-class spec))



(defn set-props
  "Merge the props of a mounted, top-level component."
  [comp props]
  (comp/set-props comp props))

(defn replace-props
  "Set the props of a mounted, top-level component."
  [comp props]
  (comp/replace-props comp props))


(defn state
  "Returns the state of a component, as set with replace-state or set-state."
  [this]
  (comp/state this))

(defn replace-state
  "Set state of a component."
  [this new-state]
  (comp/replace-state this new-state))

(defn set-state
  "Merge component state with new-state."
  [this new-state]
  (comp/set-state this new-state))


(defn props
  "Returns the props passed to a component."
  [this]
  (comp/get-props this))

(defn children
  "Returns the children passed to a component."
  [this]
  (comp/get-children this))

(defn dom-node
  "Returns the root DOM node of a mounted component."
  [this]
  (.getDOMNode this))



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
  (comp/flush))



;; Ratom

(defn atom
  "Like clojure.core/atom, except that it keeps track of derefs.
Reagent components that derefs one of these are automatically
re-rendered."
  ([x] (ratom/atom x))
  ([x & rest] (apply ratom/atom x rest)))


;; Utilities

(defn next-tick
  "Run f using requestAnimationFrame or equivalent."
  [f]
  (comp/next-tick f))

(defn partial
  "Works just like clojure.core/partial, except that it is an IFn, and
the result can be compared with ="
  [f & args]
  (util/partial-ifn. f args nil))

(let [p1 (partial vector 1 2)]
  (assert (= (p1 3) [1 2 3]))
  (assert (= p1 (partial vector 1 2)))
  (assert (ifn? p1))
  (assert (= (partial vector 1 2) p1))
  (assert (not= p1 (partial vector 1 3))))

