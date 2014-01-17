
(ns reagent.debug
  (:refer-clojure :exclude [prn println]))

(defmacro log
  "Print with console.log, if it exists."
  [& forms]
  `(when (not (nil? (.-log js/console)))
     (.log js/console ~@forms)))

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
  (let [ns (str cljs.analyzer/*cljs-ns*)]
    `(let [x# ~x]
       (println (str "dbg "
                     ~ns ":"
                     ~(:line (meta &form))
                     ": "
                     ~(pr-str x)
                     ": "
                     (pr-str x#)))
       x#)))
