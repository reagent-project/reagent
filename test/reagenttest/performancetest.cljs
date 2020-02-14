(ns reagenttest.performancetest
  (:require [reagent.core :as r]
            [reagent.impl.template :as tmpl]))

(defn hello-world-component []
  [:h1 "Hello world"])

(defn test-create-element []
  ;; TODO: Why doesn't performance dev tool show call stack for vec-to-elem?
  (js/performance.mark "functional-start")
  ; (simple-benchmark [x [hello-world-component]] (tmpl/vec-to-elem x) 100000)
  (dotimes [i 100000]
    (tmpl/vec-to-elem [hello-world-component]))
  (js/performance.mark "functional-end")
  (js/performance.measure "functional" "functional-start" "functional-end")

  (js/performance.mark "class-start")
  ; (simple-benchmark [x [^:class-component hello-world-component]] (tmpl/vec-to-elem x) 100000)
  (dotimes [i 100000]
    (tmpl/vec-to-elem [^:class-component hello-world-component]))
  (js/performance.mark "class-end")
  (js/performance.measure "class" "class-start" "class-end")
  )

(comment
  (test-create-element))
