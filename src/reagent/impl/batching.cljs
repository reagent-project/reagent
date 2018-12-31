(ns reagent.impl.batching
  (:refer-clojure :exclude [flush])
  (:require [reagent.debug :refer-macros [assert-some]]
            [reagent.impl.util :refer [is-client]]))

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
      (or (.-requestAnimationFrame w)
          (.-webkitRequestAnimationFrame w)
          (.-mozRequestAnimationFrame w)
          (.-msRequestAnimationFrame w)
          fake-raf))))

(defn compare-mount-order [c1 c2]
  (- (.-cljsMountOrder c1)
     (.-cljsMountOrder c2)))

(defn run-queue [a]
  ;; sort components by mount order, to make sure parents
  ;; are rendered before children
  (.sort a compare-mount-order)
  (dotimes [i (alength a)]
    (let [c (aget a i)]
      (when (true? (.-cljsIsDirty c))
        (.forceUpdate c)))))


;; Set from ratom.cljs
(defonce ratom-flush (fn []))

(defn run-funs [fs]
  (dotimes [i (alength fs)]
    ((aget fs i))))

(defn enqueue [queue fs f]
  (assert-some f "Enqueued function")
  (.push fs f)
  (.schedule queue ))

(deftype RenderQueue [^:mutable ^boolean scheduled?]
  Object
  (schedule [this]
    (when-not scheduled?
      (set! scheduled? true)
      (next-tick #(.run-queues this))))

  (queue-render [this c]
    (when (nil? (.-componentQueue this))
      (set! (.-componentQueue this) (array)))
    (enqueue this (.-componentQueue this) c))

  (add-before-flush [this f]
    (when (nil? (.-beforeFlush this))
      (set! (.-beforeFlush this) (array)))
    (enqueue this (.-beforeFlush this) f))

  (add-after-render [this f]
    (when (nil? (.-afterRender this))
      (set! (.-afterRender this) (array)))
    (enqueue this (.-afterRender this) f))

  (run-queues [this]
    (set! scheduled? false)
    (.flush-queues this))

  (flush-after-render [this]
    (run-funs (.-afterRender this)))

  (flush-queues [this]
    (run-funs (.-beforeFlush this))
    (ratom-flush)
    (when-some [cs (.-componentQueue this)]
      (set! (.-componentQueue this) nil)
      (run-queue cs))
    (.flush-after-render this)))

(defonce render-queue (->RenderQueue false))

(defn flush []
  (.flush-queues render-queue))

(defn flush-after-render []
  (.flush-after-render render-queue))

(defn queue-render [c]
  (when-not (.-cljsIsDirty c)
    (set! (.-cljsIsDirty c) true)
    (.queue-render render-queue c)))

(defn mark-rendered [c]
  (set! (.-cljsIsDirty c) false))

(defn do-before-flush [f]
  (.add-before-flush render-queue f))

(defn do-after-render [f]
  (.add-after-render render-queue f))

(defn schedule []
  (when (false? (.-scheduled? render-queue))
    (.schedule render-queue)))
