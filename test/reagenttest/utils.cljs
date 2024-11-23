(ns reagenttest.utils
  (:require-macros reagenttest.utils)
  (:require [promesa.core :as p]
            [react :as react]
            [reagent.core :as r]
            [reagent.debug :as debug :refer [dev?]]
            [reagent.dom :as rdom]
            [reagent.dom.server :as server]
            [reagent.impl.template :as tmpl]))

;; Should be only set for tests....
;; (set! (.-IS_REACT_ACT_ENVIRONMENT js/window) true)

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

;; "Regular versions"

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

;; Promise versions

(defn wrap-capture-console-error-promise [f]
  (fn []
    (let [org js/console.error]
      (set! js/console.error log-error)
      (-> (f)
          (p/finally
            (fn []
              (set! js/console.error org)))))))

(defn track-warnings-promise [f]
  (set! debug/tracking true)
  (reset! debug/warnings nil)
  (-> (f)
      (p/then (fn []
                @debug/warnings))
      (p/finally
        (fn []
          (reset! debug/warnings nil)
          (set! debug/tracking false)))))

(defn wrap-capture-window-error-promise [f]
  (if (exists? js/window)
    (fn []
      (let [org js/console.onerror]
        (set! js/window.onerror (fn [e]
                                  (log-error e)
                                  true))
        (-> (f)
            (p/finally
              (fn [] (set! js/window.onerror org))))))
    (fn []
      (let [process (js/require "process")
            l (fn [e]
                (log-error e))]
        (.on process "uncaughtException" l)
        (-> (f)
            (p/finally
              (fn [] (.removeListener process "uncaughtException" l))))))))

(defn act*
  "Run f to trigger Reagent updates,
  will return Promise which will resolve after
  Reagent and React render."
  [f]
  (let [p (p/deferred)]
    (f)
    (r/flush)
    (r/after-render (fn []
                      (p/resolve! p)))
    p))

(defn with-render*
  "Render the given component to a DOM node,
  after the the component is mounted to DOM,
  run the given function and wait for the Promise
  returned from the function to be resolved
  before unmounting the component from DOM."
  ([comp f]
   (with-render* comp *test-compiler* f))
  ([comp compiler f]
   (let [div (.createElement js/document "div")
         p (p/deferred)
         callback (fn []
                    (p/resolve! p))]
     (if compiler
       (rdom/render comp div {:compiler compiler
                              :callback callback})
       (rdom/render comp div callback))
     (.then p
            (fn []
              (-> (js/Promise.resolve (f div))
                  (.then (fn []
                           (rdom/unmount-component-at-node div)
                           ;; Need to wait for reagent tick after unmount
                           ;; for the ratom watches to be removed?
                           (let [p (p/deferred)]
                             (r/next-tick (fn []
                                            (p/resolve! p)))
                             p)))))))))
