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
  (when (some? cont)
    (if (== doc-node-type (.' cont :nodeType))
      (.' cont :documentElement)
      (.' cont :firstChild))))

(defn get-root-id [cont]
  (some-> (get-react-node cont)
          (.' getAttribute react-id-name)))

(defonce roots (atom {}))

(defn clear-container [node]
  ;; If render throws, React may get confused, and throw on
  ;; unmount as well, so try to force React to start over.
  (try
    (.' js/React unmountComponentAtNode node)
    (catch js/Object e
      (log e)))
  (when-let [n (get-react-node node)]
    (.' n removeAttribute react-id-name)
    (.! n :innerHTML "")))

(defn render-component [comp container callback force-update]
  (try
    (binding [*always-update* force-update]
      (.' js/React render (comp) container
          (fn []
            (binding [*always-update* false]
              (swap! roots assoc container [comp container])
              (if (some? callback)
                (callback))))))
    (catch js/Object e
      (do (clear-container container)
          (throw e)))))

(defn re-render-component [comp container]
  (render-component comp container nil true))

(defn unmount-component-at-node [container]
  (swap! roots dissoc container)
  (.' js/React unmountComponentAtNode container))

(defn force-update-all []
  (doseq [v (vals @roots)]
    (apply re-render-component v))
  "Updated")


;;; Wrapper

(deftype Wrapper [^:mutable state callback ^:mutable changed]

  IAtom

  IDeref
  (-deref [this] state)

  IReset
  (-reset! [this newval]
           (set! changed true)
           (set! state newval)
           (callback newval)
           state)

  ISwap
  (-swap! [a f]
    (-reset! a (f state)))
  (-swap! [a f x]
    (-reset! a (f state x)))
  (-swap! [a f x y]
    (-reset! a (f state x y)))
  (-swap! [a f x y more]
    (-reset! a (apply f state x y more)))

  IEquiv
  (-equiv [_ other]
          (and (instance? Wrapper other)
               ;; If either of the wrappers have changed, equality
               ;; cannot be relied on.
               (not changed)
               (not (.-changed other))
               (= state (.-state other))
               (= callback (.-callback other))))

  IPrintWithWriter
  (-pr-writer [_ writer opts]
    (-write writer "#<wrap: ")
    (pr-writer state writer opts)
    (-write writer ">")))

(defn make-wrapper [value callback-fn args]
  (Wrapper. value
            (partial-ifn. callback-fn args nil)
            false))

