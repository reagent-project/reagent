(ns reagenttest.utils
  (:require-macros reagenttest.utils)
  (:require ["react" :as react]
            [promesa.core :as p]
            [reagent.core :as r]
            [reagent.debug :as debug]
            [reagent.dom.client :as rdomc]
            [reagent.dom.server :as server]
            [reagent.impl.template :as tmpl]
            [reagent.dom :as rdom]))

(def OLD-REACT (<= (js/parseInt react/version) 18))

(when-not (.-wrapped (.-error js/console))
  (let [original-console-error (.-error js/console)]
    (set! (.-error js/console)
          (fn [& [first-arg :as args]]
            (cond
              (and OLD-REACT (string? first-arg) (.startsWith first-arg "Warning: ReactDOM.render is no longer supported in React 18."))
              nil

              (and (string? first-arg) (.startsWith first-arg "Warning: The current testing environment is not configured to support"))
              nil

              :else
              (apply original-console-error args))))
    (set! (.-wrapped (.-error js/console)) true)))

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

(defn log-warning [& f]
  (debug/warn (apply str f)))

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
        org-console-warn js/console.warn
        org-window js/window.onerror
        l (fn [e]
            (log-error e))]
    ;; console.error
    (set! js/console.error log-error)
    (set! js/console.warn log-warning)
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
      (set! js/console.warn org-console-warn)
      (reset! debug/warnings nil)
      (set! debug/tracking false)
      (if (exists? js/window)
        (set! js/window.onerror org-window)
        (let [process (js/require "process")]
          (.removeListener process "uncaughtException" l))))))

;; 16.66ms is one animation frame @ 60hz
;; NOTE: 16.7ms wasn't enough
(def RENDER-WAIT 17)

(defn act*
  "Run f to trigger Reagent updates, will return Promise which will resolve
  after Reagent and React render.

  React.act doesn't seemn to work for Reagent use so just mock with 17ms
  timeout... Hopefully that usually is enough time for React to flush the
  queue?"
  [f]
  (js/Promise.
    (fn [resolve reject]
      (try
        ;; Alternative to waiting for 1 animation frame.
        ;; Just synchronously flush Reagent queue inside React/flushSync,
        ;; to ensure React sees the updates from Reagent right-away.
        ;;
        ;; Not sure if this is preferred way for testing. The tests run faster
        ;; with this, but both Reagent and React queues are "skipped" (or manually
        ;; flushed) always.
        ; (react-dom/flushSync (fn []
        ;                        (f)
        ;                        (r/flush)))
        ; (resolve)
        (f)
        (js/setTimeout (fn []
                         (resolve))
                       RENDER-WAIT)
        (catch :default e
          ;; NOTE: errors from act body aren't logged currently
          (reject e))))))

(def ^:dynamic *render-error* nil)

(defn old-with-render*
  "Run initial render and wait for the component to be mounted on the dom and then run
  given function to check the results. If the function
  also returns a Promise or thenable, this function
  waits until that is resolved, before unmounting the
  root and resolving the Promise this function returns."
  ([comp f]
   (old-with-render* comp *test-compiler* f))
  ([comp options f]
   (let [div (.createElement js/document "div")
         first-render (p/deferred)
         callback (fn []
                    (p/resolve! first-render))
         compiler (:compiler options)
         restore-error-handlers (when (:capture-errors options)
                                  (init-capture))
         ;; Magic setup to make exception from render available to the
         ;; with-render body.
         render-error (atom nil)
         comp (if (:strict-mode? options)
                [:> react/StrictMode comp]
                comp)]
     (try
       (if compiler
         (rdom/render comp div {:compiler compiler
                                :callback callback})
         (rdom/render comp div callback))
       (catch :default e
         (reset! render-error e)
         nil))
     (-> (act* (fn [] first-render))
         ;; The callback is called even if render throws an error,
         ;; so this is always resolved.
         (p/then (fn []
                   (p/do
                     (set! *render-error* @render-error)
                     (f div)
                     (set! *render-error* nil))))
         ;; If f throws more errors, just ignore them?
         ;; Not sure if this makes sense.
         (p/catch (fn [] nil))
         (p/then (fn []
                   (rdom/unmount-component-at-node div)
                   ;; Need to wait for reagent tick after unmount
                   ;; for the ratom watches to be removed?
                   (let [ratoms-cleaned (p/deferred)]
                     (r/next-tick (fn []
                                    (p/resolve! ratoms-cleaned)))
                     ratoms-cleaned)))
         (p/finally (fn []
                      (when restore-error-handlers
                        (restore-error-handlers))))))))

(defn new-with-render*
  "Run initial render and wait for the component to be mounted on the dom and then run
  given function to check the results. If the function
  also returns a Promise or thenable, this function
  waits until that is resolved, before unmounting the
  root and resolving the Promise this function returns."
  ([comp f]
   (new-with-render* comp *test-compiler* f))
  ([comp options f]
   (let [div (.createElement js/document "div")
         compiler (:compiler options)
         strict? (:strict? options)
         restore-error-handlers (when (:capture-errors options)
                                  (init-capture))
         root (rdomc/create-root div)]
     (-> (act* (fn []
                 (rdomc/render root comp compiler strict?)))
         ;; The callback is called even if render throws an error,
         ;; so this is always resolved.
         (p/then (fn []
                   (f div)))
         (p/then (fn []
                   (rdomc/unmount root)
                   ;; Need to wait for reagent tick after unmount
                   ;; for the ratom watches to be removed?
                   (let [ratoms-cleaned (p/deferred)]
                     (r/next-tick (fn []
                                    (p/resolve! ratoms-cleaned)))
                     ratoms-cleaned)))
         (p/finally (fn []
                      (when restore-error-handlers
                        (restore-error-handlers))))))))

(def with-render* (if OLD-REACT old-with-render* new-with-render*))
