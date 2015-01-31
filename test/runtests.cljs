
(ns runtests
  (:require [testreagent]
            [testcursor]
            [testinterop]
            [testratom]
            [testwrap]
            [cljs.test :as test :include-macros true]
            [reagent.core :as reagent :refer [atom]]
            [reagent.interop :refer-macros [.' .!]]
            [reagent.debug :refer-macros [dbg println]]))

(defn all-tests []
  (test/run-tests 'testreagent
                  'testcursor
                  'testinterop
                  'testratom
                  'testwrap))

(enable-console-print!)

(def test-results (atom nil))

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
  (println "-----------------------------------------")
  (try
    (reset! test-results (all-tests))
    (catch js/Object e
      (do
        (println "Testrun failed\n" e "\n" (.-stack e))
        (reset! test-results {:error e}))))
  (println "-----------------------------------------"))

(defn ^:export run-tests []
  (if reagent/is-client
    (do
      (reset! test-results nil)
      (js/setTimeout run-all-tests 100))
    (run-all-tests)))
