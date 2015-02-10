(ns reagentdemo.dev
    (:require [reagentdemo.core]
              [reagenttest.runtests]
              [reagent.core :as r]
              [figwheel.client :as fw]))

(when r/is-client
  (fw/start
   {:websocket-url "ws://localhost:3449/figwheel-ws"}))
