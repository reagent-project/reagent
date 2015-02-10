(ns ^:figwheel-always reagenttest.runtests
  (:require [reagenttest.testreagent]
            [reagenttest.testcursor]
            [reagenttest.testinterop]
            [reagenttest.testratom]
            [reagenttest.testwrap]
            [cljs.test :as test :include-macros true]
            [reagent.core :as reagent :refer [atom]]
            [reagent.interop :refer-macros [.' .!]]
            [reagent.debug :refer-macros [dbg log]]
            [reagentdemo.core :as demo]))

(enable-console-print!)

(def test-results (atom nil))

(def test-box-style {:position 'absolute
                     :margin-left -35
                     :color :#aaa})

(defn all-tests []
  (test/run-all-tests #"reagenttest.test.*"))

(defmethod test/report [::test/default :summary] [m]
  ;; ClojureScript 2814 doesn't return anything from run-tests
  (reset! test-results m)
  (println "\nRan" (:test m) "tests containing"
    (+ (:pass m) (:fail m) (:error m)) "assertions.")
  (println (:fail m) "failures," (:error m) "errors."))

(defn test-output-mini []
  (let [res @test-results]
    [:div {:style test-box-style}
     (if res
       (if (zero? (+ (:fail res) (:error res)))
         "All tests ok"
         [:span "Test failure: "
          (:fail res) " failures, " (:error res) " errors."])
       "testing")]))

(defn run-tests []
  (reset! test-results nil)
  (if reagent/is-client
    (js/setTimeout all-tests 100)
    (all-tests)))

(reset! demo/test-results [#'test-output-mini])
(run-tests)
