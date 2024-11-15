(ns reagenttest.utils
  (:require-macros [reagenttest.utils :refer [act]])
  (:require ["react" :as react]
            [reagent.core :as r]
            [reagent.debug :as debug :refer [dev?]]
            [reagent.dom.client :as rdomc]
            [reagent.dom.server :as server]
            [reagent.impl.template :as tmpl]))

;; Should be only set for tests....
;; (set! (.-IS_REACT_ACT_ENVIRONMENT js/window) true)

(defonce original-console-error (.-error js/console))

(set! (.-error js/console)
      (fn [& [first-arg :as args]]
        (cond
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
   (let [div (.createElement js/document "div")
         root (rdomc/create-root div)]
     (try
       (let [c (if compiler
                 (rdomc/render root comp compiler)
                 (rdomc/render root comp))]
         (f c div))
       (finally
         (.unmount root)
         (r/flush))))))

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

(defn act*
  "Run f to trigger Reagent updates,
  will return Promise which will resolve after
  Reagent and React render.

  In production builds, the React.act isn't available,
  so just mock with 17ms timeout... Hopefully that usually
  is enough time for React to flush the queue?"
  [f]
  ;; async act doesn't return a real promise (with chainable then),
  ;; so wrap it.
  (if (dev?)
    (js/Promise.
      (fn [resolve reject]
        (try
          (.then (react/act f)
                 resolve
                 reject)
          (catch :default e
            (reject e)))))
    (js/Promise.
      (fn [resolve reject]
        (try
          (f)
          (js/setTimeout (fn []
                           (resolve))
                         ;; 16.6ms is one animation frame @ 60hz
                         17)
          (catch :default e
            (reject e)))))))

(defn with-render
  "Run initial render with React/act and then run
  given function to check the results. If the function
  also returns a Promise or thenable, this function
  waits until that is resolved, before unmounting the
  root and resolving the Promise this function returns."
  ([comp f]
   (with-render comp *test-compiler* f))
  ([comp compiler f]
   (let [div (.createElement js/document "div")
         root (rdomc/create-root div)]
     (.then (act (if compiler
                   (rdomc/render root comp compiler)
                   (rdomc/render root comp)))
            (fn []
              (-> (js/Promise.resolve (f div))
                  (.then (fn []
                           (.unmount root)
                           ;; TODO: Likely not needed now?
                           (r/flush)))))))))
