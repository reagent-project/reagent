(ns devsetup
  (:require
   [demo :as site]
   [runtests]
   [reagent.core :as r]
   [figwheel.client :as fw :include-macros true]))

(defn test! []
  (runtests/run-tests))

(defn on-update []
  (r/force-update-all)
  (test!))

(when r/is-client
  (fw/watch-and-reload
   :websocket-url "ws://localhost:3449/figwheel-ws"
   :jsload-callback #(on-update)))

(demo/start! {:test-results (fn []
                              [runtests/test-output-mini])})
(test!)
