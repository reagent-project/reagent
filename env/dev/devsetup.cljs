(ns devsetup
  (:require [demo :as site]
            [runtests]
            [reagent.core :as r]
            [figwheel.client :as fw :include-macros true]))

(defn on-update []
  (r/force-update-all)
  (runtests/run-tests))

(when r/is-client
  (fw/watch-and-reload
   :websocket-url "ws://localhost:3449/figwheel-ws"
   :jsload-callback #(on-update)))

(demo/start! {:test-results (fn []
                              [runtests/test-output-mini])})
(runtests/run-tests)
