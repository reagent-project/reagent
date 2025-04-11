(ns reagenttest.utils
  (:require-macros reagenttest.utils)
  (:require [react :as react]
            [promesa.core :as p]
            [reagent.core :as r]
            [reagent.debug :as debug :refer [dev?]]
            [reagent.dom.server :as server]
            [reagent.dom.client :as rdomc]
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

(defn init-capture []
  (let [org-console js/console.error
        org-window js/window.onerror
        l (fn [e]
            (log-error e))]
    ;; console.error
    (set! js/console.error log-error)
    ;; reagent.debug
    (set! debug/tracking true)
    (reset! debug/warnings nil)
    ;; window.error
    (if (exists? js/window)
      (set! js/window.onerror (fn [e]
                                (log-error e)
                                true))
      (let [process (js/require "process")]
        (.on process "uncaughtException" l)))
    (fn []
      (set! js/console.error org-console)
      (reset! debug/warnings nil)
      (set! debug/tracking false)
      (if (exists? js/window)
        (set! js/window.onerror org-window)
        (let [process (js/require "process")]
          (.removeListener process "uncaughtException" l))))))

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
  (if (and false (dev?))
    (js/Promise.
      (fn [resolve reject]
        (try
          (.then (react/act (fn []
                              (js/Promise. (fn [resolve reject]
                                             (try
                                               (f)
                                               (r/after-render (fn [] (resolve)))
                                               (catch :default e
                                                 (reject e)))))))
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

(def ^:dynamic *render-error* nil)

(defn with-render*
  "Run initial render with React/act and then run
  given function to check the results. If the function
  also returns a Promise or thenable, this function
  waits until that is resolved, before unmounting the
  root and resolving the Promise this function returns."
  ([comp f]
   (with-render* comp *test-compiler* f))
  ([comp options f]
   (let [div (.createElement js/document "div")
         first-render (p/deferred)
         callback (fn []
                    (p/resolve! first-render))
         compiler (:compiler options)
         restore-error-handlers (when (:capture-errors options)
                                  (init-capture))
         root (rdomc/create-root div)
         ;; Magic setup to make exception from render available to the
         ;; with-render body.
         render-error (atom nil)]
     (-> (act* (fn []
                 (try
                   (if compiler
                     (rdomc/render root comp compiler)
                     (rdomc/render root comp))
                   (catch :default e
                     (js/console.log "CATCH RENDER CALL" e)
                     (reset! render-error e)
                     nil))))
         (p/catch (fn [e]
                    (js/console.log "CATCH RENDER?" e)
                    (reset! render-error e)
                    (p/resolved nil)))
         ;; The callback is called even if render throws an error,
         ;; so this is always resolved.
         (p/then (fn []
                   (p/do
                     (set! *render-error* @render-error)
                     (f div)
                     (set! *render-error* nil))))
         ;; If f throws more errors, just ignore them?
         ;; Not sure if this makes sense.
         ;; TODO: Might need to catch the errors here? Maybe
         (p/catch (fn [e]
                    (js/console.log "CATCH2 RENDER?" e)
                    nil))
         (p/then (fn []
                   (.unmount root)
                   ;; Need to wait for reagent tick after unmount
                   ;; for the ratom watches to be removed?
                   (let [ratoms-cleaned (p/deferred)]
                     (r/next-tick (fn []
                                    (p/resolve! ratoms-cleaned)))
                     ratoms-cleaned)))
         (p/finally (fn []
                      (when restore-error-handlers
                        (restore-error-handlers))))))))
