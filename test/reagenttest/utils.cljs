(ns reagenttest.utils
  (:require-macros reagenttest.utils)
  (:require ["react-dom/test-utils" :as react-test]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [reagent.dom.server :as server]
            [reagent.debug :as debug]
            [reagent.impl.template :as tmpl]))

;; Silence ReactDOM.render warning
(defonce original-console-error (.-error js/console))

(set! (.-error js/console)
      (fn [& [first-arg :as args]]
        (cond
          (and (string? first-arg) (.startsWith first-arg "Warning: ReactDOM.render is no longer supported in React 18."))
          nil

          (and (string? first-arg) (.startsWith first-arg "Warning: The current testing environment is not configured to support"))
          nil

          :else
          (apply original-console-error args))))

;; The code from deftest macro will refer to these
(def class-compiler tmpl/class-compiler)
(def fn-compiler (r/create-compiler {:function-components true}))

(def ^:dynamic *test-compiler* nil)
(def ^:dynamic *test-compiler-name* nil)

(defn as-string [comp]
  (server/render-to-static-markup comp *test-compiler*))

(defn with-mounted-component
  ([comp f]
   (with-mounted-component comp *test-compiler* f))
  ([comp compiler f]
   (let [div (.createElement js/document "div")]
     (try
       (let [c (if compiler
                 (rdom/render comp div compiler)
                 (rdom/render comp div))]
         (f c div))
       (finally
         (rdom/unmount-component-at-node div)
         (r/flush))))))

(defn with-mounted-component-async
  [comp done compiler f]
  (let [div (.createElement js/document "div")
        c (if compiler
            (rdom/render comp div compiler)
            (rdom/render comp div))]
    (f c div (fn []
               (rdom/unmount-component-at-node div)
               (r/flush)
               (done)))))

(defn run-fns-after-render [& fs]
  ((reduce (fn [cb f]
             (fn []
               (r/after-render (fn []
                                 (f)
                                 (cb)))))
           (reverse fs))))

;; For testing logged errors and warnings

(defn log-error [& f]
  (debug/error (apply str f)))

(defn wrap-capture-console-error [f]
  (fn []
    (let [org js/console.error]
      (set! js/console.error log-error)
      (try
        (f)
        (finally
          (set! js/console.error org))))))

(defn wrap-capture-window-error [f]
  (if (exists? js/window)
    (fn []
      (let [org js/console.onerror]
        (set! js/window.onerror (fn [e]
                                  (log-error e)
                                  true))
        (try
          (f)
          (finally
            (set! js/window.onerror org)))))
    (fn []
      (let [process (js/require "process")
            l (fn [e]
                (log-error e))]
        (.on process "uncaughtException" l)
        (try
          (f)
          (finally
            (.removeListener process "uncaughtException" l)))))))

;; FIXME: Not useful, this isn't usable with production React.
(defn act*
  "Run f to trigger Reagent updates,
  will return Promise which will resolve after
  Reagent and React render."
  [f]
  ;; async act doesn't return a real promise (with chainable then),
  ;; so wrap it.
  (js/Promise.
    (fn [resolve reject]
      (try
        (.then (react-test/act
                 (fn reagent-act-callback []
                   ;; React act callback should return something "thenable" to use
                   ;; async act.
                   (let [p (js/Promise. (fn [resolve _reject]
                                          (r/after-render (fn reagent-act-after-reagent-flush []
                                                            (js/console.log "after render")
                                                            (resolve)))))]
                     (js/console.log "act call")
                     (f)
                     p)))
               resolve
               reject)
        (catch :default e
          (reject e))))))
