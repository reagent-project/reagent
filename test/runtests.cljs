
(ns runtests
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.debug :refer-macros [dbg println]]
            [demo :as demo]
            [cemerick.cljs.test :as t]))

(enable-console-print!)

(def test-results (atom nil))

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

(defn ^:export run-all-tests []
  (println "-----------------------------------------")
  (try
    (reset! test-results (t/run-all-tests))
    (catch js/Object e
      (do
        (println "Testrun failed\n" e "\n" (.-stack e))
        (reset! test-results {:error e}))))
  (println "-----------------------------------------"))

(if reagent/is-client
  (js/setTimeout run-all-tests 1000)
  (run-all-tests))
