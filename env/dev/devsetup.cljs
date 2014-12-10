(ns devsetup
  (:require [demo :as site]
            [runtests]
            [reagent.core :as r]
            [figwheel.client :as fw]))

(defn test-results []
  [runtests/test-output-mini])

(defn start! []
  (demo/start! {:test-results test-results})
  (runtests/run-tests))

(when r/is-client
  (fw/start
   {:websocket-url "ws://localhost:3449/figwheel-ws"
    :jsload-callback #(start!)
    :heads-up-display true
    :load-warninged-code false}))

(start!)
