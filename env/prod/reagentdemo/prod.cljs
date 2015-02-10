(ns reagentdemo.prod
  (:require [reagentdemo.core :as demo]
            [reagent.core :as r]))

(demo/start! nil)

(defn do-test []
  (when (and (exists? js/reagenttest))
    (js/reagenttest.runtests.run-tests)))

;; Wait for tests to be defined
(when r/is-client
  (r/next-tick do-test))
