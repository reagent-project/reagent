(ns reagentdemo.dev
  "Initializes the demo app, and runs the tests."
  (:require [reagentdemo.core :as core]
            [reagenttest.runtests :as tests]))

(enable-console-print!)

(defn ^:dev/after-load refresh []
  (core/init!))

(reset! core/test-component (tests/init!))
