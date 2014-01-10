(ns cloact.impl.reactimport
  (:require-macros [cloact.impl.util :refer [import-js expose-vars]]))

;; (import-js "cloact/impl/react.min.js")

(def React js/React)

;; TODO: Check event names as well

(expose-vars [:createClass
              :isValidClass
              :setProps
              :setState
              :replaceState
              :forceUpdate
              :renderComponent
              :unmountComponentAtNode
              :renderComponentToString
              :getDOMNode
              :initializeTouchEvents

              :addons
              :TransitionGroup])

