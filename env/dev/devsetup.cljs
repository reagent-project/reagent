(ns devsetup
  (:require [demo :as site]
            [runtests]
            [reagent.core :as r]
            [figwheel.client :as fw :include-macros true]))

(defn test-results []
  [runtests/test-output-mini])

;; (defn on-update []
;;   (r/force-update-all)
;;   (runtests/run-tests))

(defn start! []
  (demo/start! {:test-results test-results})
  (runtests/run-tests))

(when r/is-client
  (fw/watch-and-reload
   :websocket-url "ws://localhost:3449/figwheel-ws"
   :jsload-callback #(start!)))

(start!)
