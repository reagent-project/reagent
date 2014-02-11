(ns reagent.impl.batching
  (:refer-clojure :exclude [flush])
  (:require [reagent.debug :refer-macros [dbg log]]
            [reagent.ratom :as ratom]
            [reagent.impl.util :refer [cljs-level is-client]]
            [clojure.string :as string]))

;;; Update batching

(defn fake-raf [f]
  (js/setTimeout f 16))

(def next-tick
  (if-not is-client
    fake-raf
    (let [w js/window]
      (or (.-requestAnimationFrame w)
          (.-webkitRequestAnimationFrame w)
          (.-mozRequestAnimationFrame w)
          (.-msRequestAnimationFrame w)
          fake-raf))))

(defn compare-levels [c1 c2]
  (- (-> c1 (aget "props") (aget cljs-level))
     (-> c2 (aget "props") (aget cljs-level))))

(defn run-queue [a]
  ;; sort components by level, to make sure parents
  ;; are rendered before children
  (.sort a compare-levels)
  (dotimes [i (alength a)]
    (let [C (aget a i)]
      (when (.-cljsIsDirty C)
        (.forceUpdate C)))))

(deftype RenderQueue [^:mutable queue ^:mutable scheduled?]
  Object
  (queue-render [this C]
    (.push queue C)
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

(defn queue-render [C]
  (set! (.-cljsIsDirty C) true)
  (.queue-render render-queue C))


;; Render helper

(defn is-reagent-component [C]
  (and (not (nil? C))
       (aget C "props")
       (-> C (aget "props") (aget "cljsArgv"))))

(defn run-reactively [C run]
  (assert (is-reagent-component C))
  (set! (.-cljsIsDirty C) false)
  (let [rat (.-cljsRatom C)]
    (if (nil? rat)
      (let [res (ratom/capture-derefed run C)
            derefed (ratom/captured C)]
        (when (not (nil? derefed))
          (set! (.-cljsRatom C)
                (ratom/make-reaction run
                                     :auto-run #(queue-render C)
                                     :derefed derefed)))
        res)
      (ratom/run rat))))

(defn dispose [C]
  (let [ratom (.-cljsRatom C)]
                 (if-not (nil? ratom)
                   (ratom/dispose! ratom)))
  (set! (.-cljsIsDirty C) false))

