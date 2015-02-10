(ns ^:figwheel-always reagentdemo.dev
    (:require [reagentdemo.core :as demo]
              [reagenttest.runtests :as runtests]
              [reagent.core :as r]
              [figwheel.client :as fw]))

(when r/is-client
  (fw/start
   {:websocket-url "ws://localhost:3449/figwheel-ws"}))

(defn test-results []
  [runtests/test-output-mini])

(defn start! []
  (demo/start! {:test-results test-results})
  (when r/is-client
    (runtests/run-tests)))

(start!)
