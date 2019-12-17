(ns reagent.debug
  (:refer-clojure :exclude [prn println time])
  (:require [cljs.analyzer :as analyzer]))

(defmacro log
  "Print with console.log, if it exists."
  [& forms]
  `(when reagent.debug.has-console
     (.log js/console ~@forms)))

(defmacro warn
  "Print with console.warn."
  [& forms]
  (when *assert*
    `(when reagent.debug.has-console
       (.warn (if reagent.debug.tracking
                reagent.debug.track-console js/console)
              (str "Warning: " ~@forms)))))

(defmacro warn-unless
  [cond & forms]
  (when *assert*
    `(when (not ~cond)
       (warn ~@forms))))

(defmacro error
  "Print with console.error."
  [& forms]
  (when *assert*
    `(when reagent.debug.has-console
       (.error (if reagent.debug.tracking
                 reagent.debug.track-console js/console)
               (str ~@forms)))))

(defmacro println
  "Print string with console.log"
  [& forms]
  `(log (str ~@forms)))

(defmacro prn
  "Like standard prn, but prints using console.log (so that we get
nice clickable links to source in modern browsers)."
  [& forms]
  `(log (pr-str ~@forms)))

(defmacro dbg
  "Useful debugging macro that prints the source and value of x,
as well as package name and line number. Returns x."
  [x]
  (let [ns (str analyzer/*cljs-ns*)]
    `(let [x# ~x]
       (println (str "dbg "
                     ~ns ":"
                     ~(:line (meta &form))
                     ": "
                     ~(pr-str x)
                     ": "
                     (pr-str x#)))
       x#)))

(defmacro dev?
  "True if assertions are enabled."
  []
  (if *assert* true false))

(defmacro time [& forms]
  (let [ns (str analyzer/*cljs-ns*)
        label (str ns ":" (:line (meta &form)))]
    `(let [label# ~label
           res# (do
                  (js/console.time label#)
                  ~@forms)]
       (js/console.timeEnd label#)
       res#)))

(defmacro assert-some [value tag]
  `(assert ~value (str ~tag " must not be nil")))

(defmacro assert-component [value]
  `(assert (comp/reagent-component? ~value)
           (str "Expected a reagent component, not "
                (pr-str ~value))))

(defmacro assert-js-object [value]
  `(assert (not (map? ~value))
           (str "Expected a JS object, not "
                (pr-str ~value))))

(defmacro assert-new-state [value]
  `(assert (or (nil? ~value) (map? ~value))
           (str "Expected a valid new state, not "
                (pr-str ~value))))

(defmacro assert-callable [value]
  `(assert (ifn? ~value)
           (str "Expected something callable, not "
                (pr-str ~value))))
