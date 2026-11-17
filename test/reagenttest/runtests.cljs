(ns reagenttest.runtests
  (:require [reagenttest.testreagent]
            [reagenttest.testcursor]
            [reagenttest.testratom]
            [reagenttest.testratomasync]
            [reagenttest.testtrack]
            [reagenttest.testwithlet]
            [reagenttest.testwrap]
            [reagenttest.testhooks]
            [reagenttest.testinput]
            [reagenttest.testcompiler]
            [reagenttest.performancetest]
            [reagent.impl.template-test]
            [reagent.impl.util-test]
            [clojure.test :as test]
            [doo.runner :as doo :include-macros true]
            [jx.reporter.karma :as karma]
            [reagent.core :as r]))

(enable-console-print!)

;; nil while running, ::not-run before the first run, else the summary.
(def test-results (r/atom ::not-run))

;;; Dev page: run the tests on page load only when enabled (off by default),
;;; remembered in localStorage.

(def run-on-load-key "reagent-dev-run-tests-on-load")

(defn- stored-run-on-load? []
  (try (= "true" (.getItem js/localStorage run-on-load-key))
       (catch :default _ false)))

(defonce run-on-load (r/atom false))

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

(defn- set-run-on-load! [enabled?]
  (reset! run-on-load enabled?)
  (try (.setItem js/localStorage run-on-load-key (str enabled?))
       (catch :default _))
  (when (and enabled? (= ::not-run @test-results))
    (run-tests)))

(defn test-output-mini []
  ;; Styled by .test-output-mini in site/public/css/site.css
  (let [res @test-results
        status (cond
                 (= ::not-run res) "not-run"
                 (nil? res) "running"
                 (zero? (+ (:fail res) (:error res))) "ok"
                 :else "failed")]
    [:div {:class ["test-output-mini" status]}
     [:span.test-output-status
      (case status
        "not-run" "Tests not run"
        "running" "Running tests…"
        "ok" (str "All " (:test res) " tests ok")
        (str "Tests: " (:fail res) " failures, " (:error res) " errors"))]
     [:label
      [:input {:type "checkbox"
               :checked @run-on-load
               :on-change #(set-run-on-load! (.. % -target -checked))}]
      "Run on load"]
     [:button {:on-click run-tests}
      (if (= "not-run" status) "Run tests" "Run again")]
     [:button {:on-click (fn [_e]
                           (reagenttest.performancetest/test-create-element))}
      "Perf test"]]))

(defn init! []
  ;; This function is only used when running tests from the demo app.
  ;; Which is why exit-point is set manually.
  (when (some? #_{:clj-kondo/ignore [:inline-def]} (test/deftest empty-test))
    (doo/set-exit-point! (fn [success?] nil))
    (reset! run-on-load (stored-run-on-load?))
    (when @run-on-load
      (run-tests))
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
                 (ns-publics 'reagenttest.testhooks)
                 (ns-publics 'reagenttest.testinput)]
                (fn [var] (not (:dom (meta var))))))

;; Macro which sets *main-cli-fn*
(doo/doo-all-tests #"(reagenttest\.test.*|reagent\..*-test)")

(defn ^:export karma-tests [karma]
  (karma/run-all-tests karma #"(reagenttest\.test.*|reagent\..*-test)"))

(when (exists? js/window)
  (when-let [f (some-> js/window .-__karma__ .-loaded_real)]
    (.loaded_real (.-__karma__ js/window))))
