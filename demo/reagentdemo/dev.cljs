(ns reagentdemo.dev
  "Initializes the demo app and the test runner (tests run on load only when\n  enabled in the DEV box)."
  (:require [reagentdemo.core :as core]
            [reagenttest.runtests :as tests]))

(enable-console-print!)

(defn ^:dev/after-load refresh []
  (core/init!))

(reset! core/test-component (tests/init!))
