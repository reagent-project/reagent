(ns reagent.impl.util
  (:require [reagent.debug :refer-macros [dbg log]]
            [reagent.interop :refer-macros [.' .!]]
            [clojure.string :as string]))

(def is-client (and (exists? js/window)
                    (-> js/window (.' :document) nil? not)))

;;; Props accessors

(defn extract-props [v]
  (let [p (nth v 1 nil)]
    (if (map? p) p)))

(defn extract-children [v]
  (let [p (nth v 1 nil)
        first-child (if (or (nil? p) (map? p)) 2 1)]
    (if (> (count v) first-child)
      (subvec v first-child))))

(defn get-argv [c]
  (.' c :props.argv))

(defn get-props [c]
  (-> (.' c :props.argv) extract-props))

(defn get-children [c]
  (-> (.' c :props.argv) extract-children))

(defn reagent-component? [c]
  (-> (.' c :props.argv) nil? not))

(defn cached-react-class [c]
  (.' c :cljsReactClass))

(defn cache-react-class [c constructor]
  (.! c :cljsReactClass constructor))

;; Misc utilities

(defn memoize-1 [f]
  (let [mem (atom {})]
    (fn [arg]
      (let [v (get @mem arg)]
        (if-not (nil? v)
          v
          (let [ret (f arg)]
            (swap! mem assoc arg ret)
            ret))))))

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

; patch for CLJS-777; Can be replaced with clojure.core/ifn? after updating
; ClojureScript to a version that includes the fix:
; https://github.com/clojure/clojurescript/commit/525154f2a4874cf3b88ac3d5755794de425a94cb
(defn clj-ifn? [x]
  (or (ifn? x)
      (satisfies? IMultiFn x)))

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


(declare ^:dynamic *always-update*)

(def doc-node-type 9)
(def react-id-name "data-reactid")

(defn get-react-node [cont]
  (when-not (nil? cont)
    (if (== doc-node-type (.' cont :nodeType))
      (.' cont :documentElement)
      (.' cont :firstChild))))

(defn get-root-id [cont]
  (some-> (get-react-node cont)
          (.' getAttribute react-id-name)))

(def roots (atom {}))

(defn re-render-component [comp container]
  (try
    (.' js/React renderComponent (comp) container)
    (catch js/Object e
      (do
        (try
          (.' js/React unmountComponentAtNode container)
          (catch js/Object e
            (log e)))
        (when-let [n (get-react-node container)]
          (.' n removeAttribute react-id-name)
          (.! n :innerHTML ""))
        (throw e)))))

(defn render-component [comp container callback]
  (.' js/React renderComponent (comp) container
       (fn []
         (let [id (get-root-id container)]
           (when-not (nil? id)
             (swap! roots assoc id
                    #(re-render-component comp container))))
         (when-not (nil? callback)
           (callback)))))

(defn unmount-component-at-node [container]
  (let [id (get-root-id container)]
    (when-not (nil? id)
      (swap! roots dissoc id)))
  (.' js/React unmountComponentAtNode container))

(defn force-update-all []
  (binding [*always-update* true]
    (doseq [f (vals @roots)]
      (f)))
  "Updated")

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
                        (let [v' (nth v2 k)]
                          (if (or (identical? v v')
                                  (identical-ish? v v')
                                  (and (map? v)
                                       (shallow-equal-maps v v')))
                            res
                            (reduced false))))
                      true v1))))
