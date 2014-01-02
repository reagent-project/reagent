
(ns runtests
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing)]
                   [cloact.debug :refer [dbg println]])
  (:require [cemerick.cljs.test :as t]
            [cloact.core :as cloact :refer [atom]]
            [demo :as demo]
            [todomvc :as todomvc]))

(defn ^:export console-print [x]
  (when (not= x "\n")
    (println x)))

(set-print-fn! console-print)

(def test-results (atom nil))

(js/setTimeout
 (fn []
   (println "-----------------------------------------")
   (reset! test-results (t/run-all-tests))
   (println "-----------------------------------------"))
 1000)

(defn test-output []
  (let [res @test-results]
    [:div
     (if-not res
       [:div "waiting for tests to run"]
       [:div
        [:p (str "Ran " (:test res) " tests containing "
                 (+ (:pass res) (:fail res) (:error res))
                 " assertions.")]
        [:p (:fail res) " failues, " (:error res) " errors."]])]))

(defn examples []
  (let [p {:style {:color "#aaa"}}]
    [:div.runtests
     [demo/demo]
     [:div
      [:h2 p "Test results:"]
      [test-output]]
     [:div
      [:h2 p "Simple example:"]
      [simpleexample/simple-example]]
     [:div
      [:h2 p "Todomvc:"]
      [todomvc/todo-app]]]))

(defn test-main []
  [examples])

(defn ^:export run []
  (cloact/render-component [test-main]
                           (.-body js/document)))
