
(ns cloact.core
  (:refer-clojure :exclude [partial atom])
  (:require-macros [cloact.debug :refer [dbg prn]])
  (:require [cloact.impl.template :as tmpl]
            [cloact.impl.component :as comp]
            [cloact.impl.util :as util]
            [cloact.ratom :as ratom]))

(def React tmpl/React)


(defn render-component
  ([comp container]
     (render-component comp container nil))
  ([comp container callback]
     (.renderComponent React (tmpl/as-component comp) container callback)))

(defn unmount-component-at-node [container]
  (.unmountComponentAtNode React container))

(defn render-component-to-string
  ([component]
     (let [res (clojure.core/atom nil)]
       (render-component-to-string component #(reset! res %))
       @res))
  ([component callback]
     (.renderComponentToString React (tmpl/as-component component) callback)))

(defn create-class [body]
  (comp/create-class body))



(defn set-props [comp props]
  (comp/set-props comp props))

(defn replace-props [comp props]
  (comp/replace-props comp props))


(defn state [this]
  (comp/state this))

(defn replace-state [this new-state]
  (comp/replace-state this new-state))

(defn set-state [this new-state]
  (comp/set-state this new-state))


(defn props [this]
  (comp/get-props this))

(defn children [this]
  (comp/get-children this))

(defn dom-node [this]
  (.getDOMNode this))



(defn merge-props
  "Utility function that merges two maps, handling :class and :style
specially, like React's transferPropsTo."
  [defaults props]
  (util/merge-props defaults props))


;; Ratom

(defn atom
  "Like clojure.core/atom, except that it keeps track of derefs."
  ([x] (ratom/atom x))
  ([x & rest] (apply ratom/atom x rest)))


;; Utilities

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

