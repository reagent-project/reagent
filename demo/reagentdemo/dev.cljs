(ns reagentdemo.dev
  "Initializes the demo app, and runs the tests."
  (:require [reagent.dev]
            [reagentdemo.core :as core]
            [reagenttest.runtests :as tests]))

(reagent.dev/init-fast-refresh!)

(enable-console-print!)

(defn ^:dev/after-load init! []
  (js/console.log (reagent.dev/refresh!))
  (tests/init!))

(defonce _init (core/init!))
