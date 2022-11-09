(ns reagenttest.performancetest
  (:require [reagent.core :as r]
            [reagent.impl.template :as tmpl]
            [reagent.impl.protocols :as p]))

(defn hello-world-component []
  [:h1 "Hello world"])

(def functional-compiler (r/create-compiler {:function-components true}))

(defn test-functional []
  (js/performance.mark "functional-start")
  ; (simple-benchmark [x [hello-world-component]] (p/as-element functional-compiler x) 100000)
  (dotimes [i 100000]
    (p/as-element functional-compiler [hello-world-component]))
  (js/performance.mark "functional-end")
  (js/console.log (js/performance.measure "functional" "functional-start" "functional-end")))

(defn test-class []
  (js/performance.mark "class-start")
  ; (simple-benchmark [x [hello-world-component]] (p/as-element tmpl/default-compiler* x) 100000)
  (dotimes [i 100000]
    (p/as-element tmpl/class-compiler [hello-world-component]))
  (js/performance.mark "class-end")
  (js/console.log (js/performance.measure "class" "class-start" "class-end")))

(defn test-create-element []
  (test-functional)
  (test-class))

(comment
  (test-create-element))
