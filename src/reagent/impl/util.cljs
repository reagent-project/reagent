(ns reagent.impl.util
  (:refer-clojure :exclude [flush])
  (:require [reagent.debug :refer-macros [dbg log]]
            [reagent.ratom :as ratom]))

(def isClient (not (nil? (try (.-document js/window)
                              (catch js/Object e nil)))))

(def cljs-level "cljsLevel")

;;; Update batching

(defn fake-raf [f]
  (js/setTimeout f 16))

(def next-tick
  (if-not isClient
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


;; Misc utilities

(deftype partial-ifn [f args ^:mutable p]
  IFn
  (-invoke [_ & a]
    (or p (set! p (apply clojure.core/partial f args)))
    (apply p a))
  IEquiv
  (-equiv [_ other]
    (and (= f (.-f other)) (= args (.-args other))))
  IHash
  (-hash [_] (hash [f args])))

(defn- merge-class [p1 p2]
  (let [class (when-let [c1 (:class p1)]
                (when-let [c2 (:class p2)]
                  (str c1 " " c2)))]
    (if (nil? class)
      p2
      (assoc p2 :class class))))

(defn- merge-style [p1 p2]
  (let [style (when-let [s1 (:style p1)]
                (when-let [s2 (:style p2)]
                  (merge s1 s2)))]
    (if (nil? style)
      p2
      (assoc p2 :style style))))

(defn merge-props [p1 p2]
  (if (nil? p1)
    p2
    (do
      (assert (map? p1))
      (merge-style p1 (merge-class p1 (merge p1 p2))))))


;;; Helpers for shouldComponentUpdate

(def -not-found (js-obj))

(defn shallow-equal-maps [x y]
  ;; Compare two maps, using keyword-identical? on all values
  (or (identical? x y)
      (and (map? x)
           (map? y)
           (== (count x) (count y))
           (reduce-kv (fn [res k v]
                        (let [yv (get y k -not-found)]
                          (if (or (keyword-identical? v yv)
                                  ;; Allow :style maps, symbols
                                  ;; and reagent/partial
                                  ;; to be compared properly
                                  (and (keyword-identical? k :style)
                                       (shallow-equal-maps v yv))
                                  (and (or (identical? (type v) partial-ifn)
                                           (symbol? v))
                                       (= v yv)))
                            res
                            (reduced false))))
                      true x))))

(defn equal-args [v1 v2]
  ;; Compare two vectors using identical?
  (assert (vector? v1))
  (assert (vector? v2))
  (or (identical? v1 v2)
      (and (== (count v1) (count v2))
           (reduce-kv (fn [res k v]
                        (let [v' (v2 k)]
                          (if (or (identical? v v')
                                  (and (== 1 k)
                                       (shallow-equal-maps v v')))
                            res
                            (reduced false))))
                      true v1))))
