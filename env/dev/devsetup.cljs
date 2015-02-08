(ns ^:figwheel-always devsetup
    (:require [demo :as site]
              [runtests]
              [reagent.core :as r]
              [figwheel.client :as fw]))

(when r/is-client
  (fw/start
   {:websocket-url "ws://localhost:3449/figwheel-ws"}))

(defn test-results []
  [runtests/test-output-mini])

(defn start! []
  (demo/start! {:test-results test-results})
  (runtests/run-tests))

(start!)
