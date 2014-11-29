(ns envsetup
  (:require [demo :as site]
            [reagent.core :as r]))

(site/start! nil)

(when (exists? js/runtests)
  (js/runtests.run-tests))
