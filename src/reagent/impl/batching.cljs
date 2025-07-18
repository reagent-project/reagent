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
      (.bind (or (.-requestAnimationFrame w)
                 (.-webkitRequestAnimationFrame w)
                 (.-mozRequestAnimationFrame w)
                 (.-msRequestAnimationFrame w)
                 fake-raf)
             w))))

(defn compare-mount-order
  [^clj c1 ^clj c2]
  (- (.-cljsMountOrder c1)
     (.-cljsMountOrder c2)))

;; See reagent.dom.client/render and hydrate-root
;;
;; On React 19 react-dom/flushSync is used to flush all Reagent ratom changes
;; to DOM right away, to avoid double batching for these.
;; React strongly recommends to avoid flushSync, but run-queue is
;; used just once per animation frame and only when there were component
;; re-renders triggered from ratom changes, so maybe this is fine.
(defonce react-flush
  (fn noop [f]
    (f)))

(defn run-queue [a]
  ;; sort components by mount order, to make sure parents
  ;; are rendered before children
  (.sort a compare-mount-order)
  (react-flush
    (fn []
      (dotimes [i (alength a)]
        (let [^js/React.Component c (aget a i)]
          (when (true? (.-cljsIsDirty c))
            (.forceUpdate c)))))))


;; Set from ratom.cljs
(defonce ratom-flush (fn []))

(defn run-funs [fs]
  (dotimes [i (alength fs)]
    ((aget fs i))))

(defn enqueue [^clj queue fs f]
  (assert-some f "Enqueued function")
  (.push fs f)
  (.schedule queue))

(deftype RenderQueue [^:mutable ^boolean scheduled?]
  Object
  (schedule [this]
    (when-not scheduled?
      (set! scheduled? true)
      (next-tick #(.run-queues this))))

  (queue-render [this c]
    (when (nil? (.-componentQueue this))
      (set! (.-componentQueue this) #js []))
    (enqueue this (.-componentQueue this) c))

  (add-before-flush [this f]
    (when (nil? (.-beforeFlush this))
      (set! (.-beforeFlush this) #js []))
    (enqueue this (.-beforeFlush this) f))

  (add-after-render [this f]
    (when (nil? (.-afterRender this))
      (set! (.-afterRender this) #js []))
    (enqueue this (.-afterRender this) f))

  (run-queues [this]
    (set! scheduled? false)
    (.flush-queues this))

  (flush-before-flush [this]
    (when-some [fs (.-beforeFlush this)]
      (set! (.-beforeFlush this) nil)
      (run-funs fs)))

  (flush-render [this]
    (when-some [fs (.-componentQueue this)]
      (set! (.-componentQueue this) nil)
      (run-queue fs)))

  (flush-after-render [this]
    (when-some [fs (.-afterRender this)]
      (set! (.-afterRender this) nil)
      (run-funs fs)))

  (flush-queues [this]
    (.flush-before-flush this)
    (ratom-flush)
    (.flush-render this)
    (.flush-after-render this)))

(def render-queue (->RenderQueue false))

(defn flush []
  (.flush-queues render-queue))

(defn flush-after-render []
  (.flush-after-render render-queue))

(defn queue-render [^clj c]
  (when-not (.-cljsIsDirty c)
    (set! (.-cljsIsDirty c) true)
    (.queue-render render-queue c)))

(defn mark-rendered [^clj c]
  (set! (.-cljsIsDirty c) false))

(defn do-before-flush [f]
  (.add-before-flush render-queue f))

(defn do-after-render [f]
  (.add-after-render render-queue f))

(defn schedule []
  (when (false? (.-scheduled? render-queue))
    (.schedule render-queue)))
