(ns reagentdemo.dev
    (:require [reagentdemo.core :as demo]
              [reagenttest.runtests :as tests]
              [reagent.core :as r]
              [figwheel.client :as fw]))

(defn reload []
  (demo/init!)
  (tests/init!))

(when r/is-client
  (fw/start
   {:on-jsload reload
    :websocket-url "ws://localhost:3449/figwheel-ws"}))
