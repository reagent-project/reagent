
(ns runtests
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing)]
                   [reagent.debug :refer [dbg println]])
  (:require [cemerick.cljs.test :as t]
            [reagent.core :as reagent :refer [atom]]
            [demo :as demo]))

(enable-console-print!)

(def test-results (atom nil))

(js/setTimeout
 (fn []
   (println "-----------------------------------------")
   (reset! test-results (t/run-all-tests))
   (println "-----------------------------------------"))
 (if reagent/is-client 1000 0))

(defn test-output []
  (let [res @test-results]
    [:div {:style {:margin-top "40px"}}
     (if-not res
       [:div "waiting for tests to run"]
       [:div
        [:p (str "Ran " (:test res) " tests containing "
                 (+ (:pass res) (:fail res) (:error res))
                 " assertions.")]
        [:p (:fail res) " failues, " (:error res) " errors."]])]))

(defn test-output-mini []
  (let [res @test-results]
    (if res
      (if (zero? (+ (:fail res) (:error res)))
        [:div "Tests ok"]
        [test-output])
      [:div "."])))

(defn test-demo []
  [:div
   [test-output]
   [demo/demo]])

(defn ^:export mounttests []
  (reagent/render-component [test-demo] (.-body js/document)))
