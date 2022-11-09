(ns reagent.core
  (:require-macros [reagent.core])
  (:refer-clojure :exclude [partial atom flush])
  (:require [react :as react]
            [reagent.impl.template :as tmpl]
            [reagent.impl.component :as comp]
            [reagent.impl.util :as util]
            [reagent.impl.batching :as batch]
            [reagent.impl.protocols :as p]
            [reagent.ratom :as ratom]
            [reagent.debug :as deb :refer-macros [assert-some assert-component
                                                  assert-js-object assert-new-state
                                                  assert-callable]]))

(def is-client util/is-client)

(defn create-element
  "Create a native React element, by calling React.createElement directly.

  That means the second argument must be a javascript object (or nil), and
  that any Reagent hiccup forms must be processed with as-element. For example
  like this:

  ```cljs
  (r/create-element \"div\" #js{:className \"foo\"}
    \"Hi \" (r/as-element [:strong \"world!\"])
  ```

  which is equivalent to

  ```cljs
  [:div.foo \"Hi\" [:strong \"world!\"]]
  ```"
  ([type]
   (create-element type nil))
  ([type props]
   (assert-js-object props)
   (react/createElement type props))
  ([type props child]
   (assert-js-object props)
   (react/createElement type props child))
  ([type props child & children]
   (assert-js-object props)
   (apply react/createElement type props child children)))

(defn as-element
  "Turns a vector of Hiccup syntax into a React element. Returns form
  unchanged if it is not a vector."
  ([form] (p/as-element tmpl/*current-default-compiler* form))
  ([form compiler] (p/as-element compiler form)))

(defn adapt-react-class
  "Returns an adapter for a native React class, that may be used
  just like a Reagent component function or class in Hiccup forms."
  [c]
  (assert-some c "Component")
  (tmpl/adapt-react-class c))

(defn reactify-component
  "Returns an adapter for a Reagent component, that may be used from
  React, for example in JSX. A single argument, props, is passed to
  the component, converted to a map."
  ([c] (reactify-component c tmpl/*current-default-compiler*))
  ([c compiler]
   (assert-some c "Component")
   (comp/reactify-component c compiler)))

(defn create-class
  "Creates JS class based on provided Clojure map, for example:

  ```cljs
  {;; Constructor
   :constructor (fn [this props])
   :get-initial-state (fn [this])
   ;; Static methods
   :get-derived-state-from-props (fn [props state] partial-state)
   :get-derived-state-from-error (fn [error] partial-state)
   ;; Methods
   :get-snapshot-before-update (fn [this old-argv new-argv] snapshot)
   :should-component-update (fn [this old-argv new-argv])
   :component-did-mount (fn [this])
   :component-did-update (fn [this old-argv old-state snapshot])
   :component-will-unmount (fn [this])
   :component-did-catch (fn [this error info])
   :reagent-render (fn [args....])
   ;; Or alternatively:
   :render (fn [this])
   ;; Deprecated methods:
   :UNSAFE_component-will-receive-props (fn [this new-argv])
   :UNSAFE_component-will-update (fn [this new-argv new-state])
   :UNSAFE_component-will-mount (fn [this])}
  ```

  Everything is optional, except either :reagent-render or :render.

  Map keys should use `React.Component` method names (https://reactjs.org/docs/react-component.html),
  and can be provided in snake-case or camelCase.

  State can be initialized using constructor, which matches React.Component class,
  or using getInitialState which matches old React createClass function and is
  now implemented by Reagent for compatibility.

  State can usually be anything, e.g. Cljs object. But if using getDerivedState
  methods, the state has to be plain JS object as React implementation uses
  Object.assign to merge partial state into the current state.

  React built-in static methods or properties are automatically defined as statics."
  ([spec]
   (comp/create-class spec tmpl/*current-default-compiler*))
  ([spec compiler]
   (comp/create-class spec compiler)))


(defn current-component
  "Returns the current React component (a.k.a `this`) in a component
  function."
  []
  comp/*current-component*)

(defn state-atom
  "Returns an atom containing a components state."
  [this]
  (assert-component this)
  (comp/state-atom this))

(defn state
  "Returns the state of a component, as set with replace-state or set-state.
  Equivalent to `(deref (r/state-atom this))`"
  [this]
  (assert-component this)
  (deref (state-atom this)))

(defn replace-state
  "Set state of a component.
  Equivalent to `(reset! (state-atom this) new-state)`"
  [this new-state]
  (assert-component this)
  (assert-new-state new-state)
  (reset! (state-atom this) new-state))

(defn set-state
  "Merge component state with new-state.
  Equivalent to `(swap! (state-atom this) merge new-state)`"
  [this new-state]
  (assert-component this)
  (assert-new-state new-state)
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
  (assert-component this)
  (comp/get-props this))

(defn children
  "Returns the children passed to a component."
  [this]
  (assert-component this)
  (comp/get-children this))

(defn argv
  "Returns the entire Hiccup form passed to the component."
  [this]
  (assert-component this)
  (comp/get-argv this))

(defn class-names
  "Function which normalizes and combines class values to a string

  Reagent allows classes to be defined as:
  - Strings
  - Named objects (Symbols or Keywords)
  - Collections of previous types"
  ([])
  ([class] (util/class-names class))
  ([class1 class2] (util/class-names class1 class2))
  ([class1 class2 & others] (apply util/class-names class1 class2 others)))

(defn merge-props
  "Utility function that merges some maps, handling `:class` and `:style`.

  The :class value is always normalized (using `class-names`) even if no
  merging is done."
  ([] (util/merge-props))
  ([defaults] (util/merge-props defaults))
  ([defaults props] (util/merge-props defaults props))
  ([defaults props & others] (apply util/merge-props defaults props others)))

(defn flush
  "Render dirty components immediately.

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

  In other words, `@(track foo bar)` will produce the same result
  as `(foo bar)`, but foo will only be called again when the atoms it
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

  ```cljs
  (wrap (:foo @state)
        swap! state assoc :foo)
  ```

  Probably useful only for passing to child components."
  [value reset-fn & args]
  (assert-callable reset-fn)
  (ratom/make-wrapper value reset-fn args))


;; RCursor

(defn cursor
  "Provide a cursor into a Reagent atom.

  Behaves like a Reagent atom but focuses updates and derefs to
  the specified path within the wrapped Reagent atom. e.g.,

  ```cljs
  (let [c (cursor ra [:nested :content])]
    ... @c ;; equivalent to (get-in @ra [:nested :content])
    ... (reset! c 42) ;; equivalent to (swap! ra assoc-in [:nested :content] 42)
    ... (swap! c inc) ;; equivalence to (swap! ra update-in [:nested :content] inc)
    )
  ```

  The first parameter can also be a function, that should look
  something like this:

  ```cljs
  (defn set-get
    ([k] (get-in @state k))
    ([k v] (swap! state assoc-in k v)))
  ```

  The function will be called with one argument – the path passed to
  cursor – when the cursor is deref'ed, and two arguments (path and
  new value) when the cursor is modified.

  Given that set-get function, (and that state is a Reagent atom, or
  another cursor) these cursors are equivalent:
  `(cursor state [:foo])` and `(cursor set-get [:foo])`.

  Note that a cursor is lazy: its value will not change until it is
  used. This may be noticed with add-watch."
  ([src path]
   (ratom/cursor src path)))


;; Utilities

(defn rswap!
  "Swaps the value of a to be `(apply f current-value-of-atom args)`.

  rswap! works like swap!, except that recursive calls to rswap! on
  the same atom are allowed – and it always returns nil."
  [^IAtom a f & args]
  {:pre [(satisfies? IAtom a)
         (ifn? f)]}
  (if (.-rswapping a)
    (-> (or (.-rswapfs a)
            (set! (.-rswapfs a) #js []))
        (.push #(apply f % args)))
    (do (set! (.-rswapping a) true)
        (try (swap! a (fn [state]
                        (loop [s (apply f state args)]
                          (if-some [sf (some-> a .-rswapfs .shift)]
                            (recur (sf s))
                            s))))
             (finally
               (set! (.-rswapping a) false)))))
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
  "Works just like clojure.core/partial, but the result can be compared with ="
  [f & args]
  (util/make-partial-fn f args))

(defn create-compiler
  "Creates Compiler object with given `opts`,
  this can be passed to `render`, `as-element` and other functions to control
  how they turn the Reagent-style Hiccup into React components and elements."
  [opts]
  (tmpl/create-compiler opts))

(defn set-default-compiler!
  "Globally sets the Compiler object used by `render`, `as-element` and other
  calls by default, when no `compiler` parameter is provided.

  Use `nil` value to restore the original default compiler."
  [compiler]
  (tmpl/set-default-compiler! (if (nil? compiler)
                                tmpl/class-compiler
                                compiler)))

(defn render
  {:deprecated "0.10.0"
   :superseded-by "reagent.dom/render"}
  [& _]
  (throw (js/Error. "Reagent.core/render function was moved to reagent.dom namespace in Reagent v1.0.")))
