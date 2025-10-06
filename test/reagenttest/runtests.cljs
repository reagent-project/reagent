(ns reagenttest.runtests
  (:require [reagenttest.testreagent]
            [reagenttest.testcursor]
            [reagenttest.testratom]
            [reagenttest.testratomasync]
            [reagenttest.testtrack]
            [reagenttest.testwithlet]
            [reagenttest.testwrap]
            [reagenttest.testhooks]
            [reagenttest.performancetest]
            [reagent.impl.template-test]
            [reagent.impl.util-test]
            [clojure.test :as test]
            [doo.runner :as doo :include-macros true]
            [jx.reporter.karma :as karma]
            [reagent.core :as r]))

(enable-console-print!)

(def test-results (r/atom nil))

(def test-box-style {:position "absolute"
                     :top 5
                     :width 170
                     :right 5
                     :border "1px solid #444"
                     :padding "3px"
                     :background-color "#A88"
                     :color "#000"})

(def btn-style {:color "#000"
                :background "#CCC"
                :padding "2px"
                :cursor "pointer"})

(defn all-tests []
  (test/run-all-tests #"(reagenttest\.test.*|reagent\..*-test)"))

(defmethod test/report [::test/default :summary] [m]
  ;; ClojureScript 2814 doesn't return anything from run-tests
  (reset! test-results m)
  (println "\nRan" (:test m) "tests containing"
    (+ (:pass m) (:fail m) (:error m)) "assertions.")
  (println (:fail m) "failures," (:error m) "errors."))

(defn run-tests []
  (reset! test-results nil)
  (if r/is-client
    (js/setTimeout all-tests 100)
    (all-tests)))

(defn test-output-mini []
  (let [res @test-results]
    [:div
     {:class "test-output-mini"
      :style test-box-style}
     (if res
       (if (zero? (+ (:fail res) (:error res)))
         "All tests ok"
         (str "Test failure: "
              (:fail res) " failures, " (:error res) " errors."))
       "testing")
     [:div
      {:style {:display "flex"
               :flex-direction "row"
               :gap "3px"}}
      [:button
       {:on-click run-tests
        :style btn-style}
       "Run again"]
      [:button
       {:on-click (fn [_e]
                    (reagenttest.performancetest/test-create-element))
        :style btn-style}
       "Perf test"]]]))

(defn init! []
  ;; This function is only used when running tests from the demo app.
  ;; Which is why exit-point is set manually.
  (when (some? (test/deftest empty-test))
    (doo/set-exit-point! (fn [success?] nil))
    (run-tests)
    [#'test-output-mini]))

;; From cognitect-labs/test-runner.
;; Will modify test vars based on filter-fn, based on
;; the metadata on test vars, so that
;; test run in cljs.test will not see ignored test vars.
(defn filter-vars! [ns-syms filter-fn]
  (doseq [ns-sym ns-syms]
    (doseq [[_ var] ns-sym]
      (when (:test (meta var))
        (when (not (filter-fn var))
          (set! (.-cljs$lang$test @var) nil))))))

(goog-define ^boolean DOM_TESTS true)

(when (false? DOM_TESTS)
  (js/console.log "DOM tests disabled")
  ;; Filter vars on namespaces using ^:dom metadata on test vars.
  (filter-vars! [(ns-publics 'reagenttest.testreagent)
                 (ns-publics 'reagenttest.testwrap)
                 (ns-publics 'reagenttest.testhooks)]
                (fn [var] (not (:dom (meta var))))))

;; Macro which sets *main-cli-fn*
(doo/doo-all-tests #"(reagenttest\.test.*|reagent\..*-test)")

(defn ^:export karma-tests [karma]
  (karma/run-all-tests karma #"(reagenttest\.test.*|reagent\..*-test)"))

(when (exists? js/window)
  (when-let [f (some-> js/window .-__karma__ .-loaded_real)]
    (.loaded_real (.-__karma__ js/window))))
