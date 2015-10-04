(ns reagent.impl.batching
  (:refer-clojure :exclude [flush])
  (:require [reagent.debug :refer-macros [dbg]]
            [reagent.interop :refer-macros [.' .!]]
            [reagent.impl.util :refer [is-client]]
            [clojure.string :as string]))

;;; Update batching

(defonce mount-count 0)

(defn next-mount-count []
  (set! mount-count (inc mount-count)))

(defn fake-raf [f]
  (js/setTimeout f 16))

(def next-tick
  (if-not is-client
    fake-raf
    (let [w js/window]
      (or (.' w :requestAnimationFrame)
          (.' w :webkitRequestAnimationFrame)
          (.' w :mozRequestAnimationFrame)
          (.' w :msRequestAnimationFrame)
          fake-raf))))

(defn compare-mount-order [c1 c2]
  (- (.' c1 :cljsMountOrder)
     (.' c2 :cljsMountOrder)))

(defn run-queue [a]
  ;; sort components by mount order, to make sure parents
  ;; are rendered before children
  (.sort a compare-mount-order)
  (dotimes [i (alength a)]
    (let [c (aget a i)]
      (when (true? (.' c :cljsIsDirty))
        (.! c :cljsIsDirty false)
        (.' c forceUpdate)))))

(defn run-funs [a]
  (dotimes [i (alength a)]
    ((aget a i))))

;; Set from ratom.cljs
(defonce ratom-flush identity)

(deftype RenderQueue [^:mutable queue ^:mutable ^boolean scheduled?
                      ^:mutable after-render]
  Object
  (queue-render [this c]
    (.push queue c)
    (.schedule this))
  (add-after-render [_ f]
    (.push after-render f))
  (schedule [this]
    (when-not scheduled?
      (set! scheduled? true)
      (next-tick #(.run-queue this))))
  (run-queue [_]
    (ratom-flush)
    (let [q queue
          aq after-render]
      (set! queue (array))
      (set! after-render (array))
      (set! scheduled? false)
      (run-queue q)
      (run-funs aq))))

(defonce render-queue (RenderQueue. (array) false (array)))

(defn flush []
  (.run-queue render-queue))

(defn queue-render [c]
  (.! c :cljsIsDirty true)
  (.queue-render render-queue c))

(defn mark-rendered [c]
  (.! c :cljsIsDirty false))

(defn do-after-flush [f]
  (.add-after-render render-queue f))

(defn do-later [f]
  (do-after-flush f)
  (.schedule render-queue))

(defn schedule []
  (.schedule render-queue))
