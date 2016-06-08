(ns reagent.core
  (:require-macros [reagent.core])
  (:refer-clojure :exclude [partial atom flush])
  (:require [reagent.impl.template :as tmpl]
            [reagent.impl.component :as comp]
            [reagent.impl.util :as util]
            [reagent.impl.batching :as batch]
            [reagent.ratom :as ratom]
            [reagent.debug :as deb :refer-macros [dbg prn]]
            [reagent.interop :refer-macros [$ $!]]
            [reagent.dom :as dom]
            [reagent.dom.server :as server]))

(def is-client util/is-client)

(def react util/react)

(defn create-element
  "Create a native React element, by calling React.createElement directly.

  That means the second argument must be a javascript object (or nil), and
  that any Reagent hiccup forms must be processed with as-element. For example
  like this:

    (r/create-element \"div\" #js{:className \"foo\"}
       \"Hi \" (r/as-element [:strong \"world!\"])

  which is equivalent to

    [:div.foo \"Hi\" [:strong \"world!\"]]"
  ([type]
   (create-element type nil))
  ([type props]
   (assert (not (map? props)))
   ($ react createElement type props))
  ([type props child]
   (assert (not (map? props)))
   ($ react createElement type props child))
  ([type props child & children]
   (assert (not (map? props)))
   (apply ($ react :createElement) type props child children)))

(defn as-element
  "Turns a vector of Hiccup syntax into a React element. Returns form
  unchanged if it is not a vector."
  [form]
  (tmpl/as-element form))

(defn adapt-react-class
  "Returns an adapter for a native React class, that may be used
  just like a Reagent component function or class in Hiccup forms."
  [c]
  (assert c)
  (tmpl/adapt-react-class c))

(defn reactify-component
  "Returns an adapter for a Reagent component, that may be used from
  React, for example in JSX. A single argument, props, is passed to
  the component, converted to a map."
  [c]
  (assert c)
  (comp/reactify-component c))

(defn render
  "Render a Reagent component into the DOM. The first argument may be
  either a vector (using Reagent's Hiccup syntax), or a React element.
  The second argument should be a DOM node.

  Optionally takes a callback that is called when the component is in place.

  Returns the mounted component instance."
  ([comp container]
   (dom/render comp container))
  ([comp container callback]
   (dom/render comp container callback)))

(defn unmount-component-at-node
  "Remove a component from the given DOM node."
  [container]
  (dom/unmount-component-at-node container))

(defn render-to-string
  "Turns a component into an HTML string."
  [component]
  (server/render-to-string component))

;; For backward compatibility
(def as-component as-element)
(def render-component render)
(def render-component-to-string render-to-string)

(defn render-to-static-markup
  "Turns a component into an HTML string, without data-react-id attributes, etc."
  [component]
  (server/render-to-static-markup component))

(defn ^:export force-update-all
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
  (dom/force-update-all)
  (batch/flush-after-render))

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
     :reagent-render (fn [args....])}   ;; or :render (fn [this])

  Everything is optional, except either :reagent-render or :render."
  [spec]
  (comp/create-class spec))


(defn current-component
  "Returns the current React component (a.k.a this) in a component
  function."
  []
  comp/*current-component*)

(defn state-atom
  "Returns an atom containing a components state."
  [this]
  (assert (comp/reagent-component? this))
  (comp/state-atom this))

(defn state
  "Returns the state of a component, as set with replace-state or set-state.
  Equivalent to (deref (r/state-atom this))"
  [this]
  (assert (comp/reagent-component? this))
  (deref (state-atom this)))

(defn replace-state
  "Set state of a component.
  Equivalent to (reset! (state-atom this) new-state)"
  [this new-state]
  (assert (comp/reagent-component? this))
  (assert (or (nil? new-state) (map? new-state)))
  (reset! (state-atom this) new-state))

(defn set-state
  "Merge component state with new-state.
  Equivalent to (swap! (state-atom this) merge new-state)"
  [this new-state]
  (assert (comp/reagent-component? this))
  (assert (or (nil? new-state) (map? new-state)))
  (swap! (state-atom this) merge new-state))

(defn force-update
  "Force a component to re-render immediately.

  If the second argument is true, child components will also be
  re-rendered, even is their arguments have not changed."
  ([this]
   (force-update this false))
  ([this deep]
   (ratom/flush!)
   (util/force-update this deep)
   (batch/flush-after-render)))

(defn props
  "Returns the props passed to a component."
  [this]
  (assert (comp/reagent-component? this))
  (comp/get-props this))

(defn children
  "Returns the children passed to a component."
  [this]
  (assert (comp/reagent-component? this))
  (comp/get-children this))

(defn argv
  "Returns the entire Hiccup form passed to the component."
  [this]
  (assert (comp/reagent-component? this))
  (comp/get-argv this))

(defn dom-node
  "Returns the root DOM node of a mounted component."
  [this]
  (dom/dom-node this))

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

(defn track
  "Takes a function and optional arguments, and returns a derefable
  containing the output of that function. If the function derefs
  Reagent atoms (or track, etc), the value will be updated whenever
  the atom changes.

  In other words, @(track foo bar) will produce the same result
  as (foo bar), but foo will only be called again when the atoms it
  depends on changes, and will only trigger updates of components when
  its result changes.

  track is lazy, i.e the function is only evaluated on deref."
  [f & args]
  {:pre [(ifn? f)]}
  (ratom/make-track f args))

(defn track!
  "An eager version of track. The function passed is called
  immediately, and continues to be called when needed, until stopped
  with dispose!."
  [f & args]
  {:pre [(ifn? f)]}
  (ratom/make-track! f args))

(defn dispose!
  "Stop the result of track! from updating."
  [x]
  (ratom/dispose! x))

(defn wrap
  "Provide a combination of value and callback, that looks like an atom.

  The first argument can be any value, that will be returned when the
  result is deref'ed.

  The second argument should be a function, that is called with the
  optional extra arguments provided to wrap, and the new value of the
  resulting 'atom'.

  Use for example like this:

  (wrap (:foo @state)
        swap! state assoc :foo)

  Probably useful only for passing to child components."
  [value reset-fn & args]
  (assert (ifn? reset-fn))
  (ratom/make-wrapper value reset-fn args))


;; RCursor

(defn cursor
  "Provide a cursor into a Reagent atom.

  Behaves like a Reagent atom but focuses updates and derefs to
  the specified path within the wrapped Reagent atom. e.g.,
    (let [c (cursor ra [:nested :content])]
      ... @c ;; equivalent to (get-in @ra [:nested :content])
      ... (reset! c 42) ;; equivalent to (swap! ra assoc-in [:nested :content] 42)
      ... (swap! c inc) ;; equivalence to (swap! ra update-in [:nested :content] inc)
      )

  The first parameter can also be a function, that should look
  something like this:

    (defn set-get
      ([k] (get-in @state k))
      ([k v] (swap! state assoc-in k v)))

  The function will be called with one argument – the path passed to
  cursor – when the cursor is deref'ed, and two arguments (path and
  new value) when the cursor is modified.

  Given that set-get function, (and that state is a Reagent atom, or
  another cursor) these cursors are equivalent:
  (cursor state [:foo]) and (cursor set-get [:foo]).

  Note that a cursor is lazy: its value will not change until it is
  used. This may be noticed with add-watch."
  ([src path]
   (ratom/cursor src path)))


;; Utilities

(defn rswap!
  "Swaps the value of a to be (apply f current-value-of-atom args).

  rswap! works like swap!, except that recursive calls to rswap! on
  the same atom are allowed – and it always returns nil."
  [a f & args]
  {:pre [(satisfies? IAtom a)
         (ifn? f)]}
  (if a.rswapping
    (-> (or a.rswapfs (set! a.rswapfs (array)))
        (.push #(apply f % args)))
    (do (set! a.rswapping true)
        (try (swap! a (fn [state]
                        (loop [s (apply f state args)]
                          (if-some [sf (some-> a.rswapfs .shift)]
                            (recur (sf s))
                            s))))
             (finally
               (set! a.rswapping false)))))
  nil)

(defn next-tick
  "Run f using requestAnimationFrame or equivalent.

  f will be called just before components are rendered."
  [f]
  (batch/do-before-flush f))

(defn after-render
  "Run f using requestAnimationFrame or equivalent.

  f will be called just after any queued renders in the next animation
  frame (and even if no renders actually occur)."
  [f]
  (batch/do-after-render f))

(defn partial
  "Works just like clojure.core/partial, except that it is an IFn, and
  the result can be compared with ="
  [f & args]
  (util/partial-ifn. f args nil))

(defn component-path
  ;; Try to return the path of component c as a string.
  ;; Maybe useful for debugging and error reporting, but may break
  ;; with future versions of React (and return nil).
  [c]
  (comp/component-path c))
