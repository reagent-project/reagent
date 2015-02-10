(ns reagenttest.runtests
  (:require [reagenttest.testreagent]
            [reagenttest.testcursor]
            [reagenttest.testinterop]
            [reagenttest.testratom]
            [reagenttest.testwrap]
            [cljs.test :as test :include-macros true]
            [reagent.core :as reagent :refer [atom]]
            [reagent.interop :refer-macros [.' .!]]
            [reagent.debug :refer-macros [dbg log]]))

(enable-console-print!)

(defn all-tests []
  (test/run-tests 'reagenttest.testreagent
                  'reagenttest.testcursor
                  'reagenttest.testinterop
                  'reagenttest.testratom
                  'reagenttest.testwrap))

(def test-results (atom nil))

(defmethod test/report [::test/default :summary] [m]
  ;; ClojureScript 2814 doesn't return anything from run-tests
  (reset! test-results m)
  (println "\nRan" (:test m) "tests containing"
    (+ (:pass m) (:fail m) (:error m)) "assertions.")
  (println (:fail m) "failures," (:error m) "errors."))

(def test-box {:position 'absolute
               :margin-left -35
               :color :#aaa})

(defn test-output []
  (let [res @test-results]
    [:div {:style test-box}
     (if-not res
       [:div "waiting for tests to run"]
       [:div
        [:p (str "Ran " (:test res) " tests containing "
                 (+ (:pass res) (:fail res) (:error res))
                 " assertions.")]
        [:p (:fail res) " failures, " (:error res) " errors."]])]))

(defn test-output-mini []
  (let [res @test-results]
    (if res
      (if (zero? (+ (:fail res) (:error res)))
        [:div {:style test-box}
         "All tests ok"]
        [test-output])
      [:div {:style test-box} "testing"])))

(defn ^:export run-all-tests []
  (log "-----------------------------------------")
  (try
    (reset! test-results nil)
    (all-tests)
    (catch js/Object e
      (do
        (log "Testrun failed\n" e "\n" (.-stack e))
        (reset! test-results {:error e}))))
  (log "-----------------------------------------"))

(defn ^:export run-tests []
  (if reagent/is-client
    (do
      (reset! test-results nil)
      (js/setTimeout run-all-tests 100))
    (run-all-tests)))
