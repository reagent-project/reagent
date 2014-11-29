(ns envsetup
  (:require [demo :as site]
            [reagent.core :as r]))

(site/start! nil)

(defn do-test []
  (when (exists? js/runtests)
    (js/runtests.run-tests)))

;; Wait for tests to be defined
(r/next-tick do-test)
