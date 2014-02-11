(ns reagent.impl.util
  (:refer-clojure :exclude [flush])
  (:require [reagent.debug :refer-macros [dbg log]]
            [reagent.ratom :as ratom]
            [reagent.impl.reactimport :as reactimport]
            [clojure.string :as string]))

(def is-client (not (nil? (try (.-document js/window)
                               (catch js/Object e nil)))))

(def cljs-level "cljsLevel")
(def cljs-argv "cljsArgv")
(def React reactimport/React)

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


;; Misc utilities

(def dont-camel-case #{"aria" "data"})

(defn capitalize [s]
  (if (< (count s) 2)
    (string/upper-case s)
    (str (string/upper-case (subs s 0 1)) (subs s 1))))

(defn dash-to-camel [dashed]
  (if (string? dashed)
    dashed
    (let [name-str (name dashed)
          [start & parts] (string/split name-str #"-")]
      (if (dont-camel-case start)
        name-str
        (apply str start (map capitalize parts))))))


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

(defn identical-ish? [x y]
  (or (keyword-identical? x y)
      (and (or (symbol? x)
               (identical? (type x) partial-ifn))
           (= x y))))

(defn shallow-equal-maps [x y]
  ;; Compare two maps, using identical-ish? on all values
  (or (identical? x y)
      (and (map? x)
           (map? y)
           (== (count x) (count y))
           (reduce-kv (fn [res k v]
                        (let [yv (get y k -not-found)]
                          (if (or (identical? v yv)
                                  (identical-ish? v yv)
                                  ;; Handle :style maps specially
                                  (and (keyword-identical? k :style)
                                       (shallow-equal-maps v yv)))
                            res
                            (reduced false))))
                      true x))))

(defn equal-args [v1 v2]
  ;; Compare two vectors using identical-ish?
  (assert (vector? v1))
  (assert (vector? v2))
  (or (identical? v1 v2)
      (and (== (count v1) (count v2))
           (reduce-kv (fn [res k v]
                        (let [v' (v2 k)]
                          (if (or (identical? v v')
                                  (identical-ish? v v')
                                  (and (== 1 k)
                                       (shallow-equal-maps v v')))
                            res
                            (reduced false))))
                      true v1))))
