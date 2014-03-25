(ns reagent.impl.batching
  (:refer-clojure :exclude [flush])
  (:require [reagent.debug :refer-macros [dbg]]
            [reagent.interop :refer-macros [.' .!]]
            [reagent.ratom :as ratom]
            [reagent.impl.util :refer [is-client]]
            [clojure.string :as string]))

;;; Update batching

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

(defn compare-levels [c1 c2]
  (- (.' c1 :props.level)
     (.' c2 :props.level)))

(defn run-queue [a]
  ;; sort components by level, to make sure parents
  ;; are rendered before children
  (.sort a compare-levels)
  (dotimes [i (alength a)]
    (let [c (aget a i)]
      (when (.' c :cljsIsDirty)
        (.' c forceUpdate)))))

(deftype RenderQueue [^:mutable queue ^:mutable scheduled?]
  Object
  (queue-render [this c]
    (.push queue c)
    (.schedule this))
  (schedule [this]
    (when-not scheduled?
      (set! scheduled? true)
      (next-tick #(.run-queue this))))
  (run-queue [_]
    (let [q queue]
      (set! queue (array))
      (set! scheduled? false)
      (run-queue q))))

(def render-queue (RenderQueue. (array) false))

(defn flush []
  (.run-queue render-queue))

(defn queue-render [c]
  (.! c :cljsIsDirty true)
  (.queue-render render-queue c))

(defn mark-rendered [c]
  (.! c :cljsIsDirty false))

;; Render helper

(defn is-reagent-component [c]
  (some-> c (.' :props) (.' :argv)))

(defn run-reactively [c run]
  (assert (is-reagent-component c))
  (mark-rendered c)
  (let [rat (.' c :cljsRatom)]
    (if (nil? rat)
      (let [res (ratom/capture-derefed run c)
            derefed (ratom/captured c)]
        (when (not (nil? derefed))
          (.! c :cljsRatom
              (ratom/make-reaction run
                                   :auto-run #(queue-render c)
                                   :derefed derefed)))
        res)
      (ratom/run rat))))

(defn dispose [c]
  (some-> (.' c :cljsRatom)
          ratom/dispose!)
  (mark-rendered c))

