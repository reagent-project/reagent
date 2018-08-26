(ns example.utils.http-fx
  "Helper functions to make HTTP requests / handling cleaner."
  (:require [ajax.core :as ajax]
            [goog.net.ErrorCode :as errors]
            [clojure.string :as str]
            [cljs.spec.alpha :as spec]
            [re-frame.core :refer [reg-fx dispatch]]))


(def <sub (comp deref re-frame.core/subscribe))
(def >evt re-frame.core/dispatch)

(defn set-location
  "sets the browser location"
  [path]
  (set! (.-location js/window) path))

;; Inspired by day8 http-fx lib


(defn ajax-xhrio-handler
  "ajax-request only provides a single handler for success and errors"
  [on-success on-failure xhrio [success? response]]
  ; see http://docs.closure-library.googlecode.com/git/class_goog_net_XhrIo.html
  (if success?
    (on-success response)
    (let [details (merge
                    {:uri             (.getLastUri xhrio)
                     :last-method     (.-lastMethod_ xhrio)
                     :last-error      (.getLastError xhrio)
                     :last-error-code (.getLastErrorCode xhrio)
                     :debug-message   (-> xhrio .getLastErrorCode (errors/getDebugMessage))}
                    response)]
      (on-failure details))))

(defn request->xhrio-options
  [{:as   request
    :keys [on-success on-failure]
    :or   {on-success      [:http-no-on-success]
           on-failure      [:http-no-on-failure]}}]

  ;; wrap events in cljs-ajax callback
  (let [api (new js/goog.net.XhrIo)]
    (-> request
        (assoc
          :api     api
          :handler (partial ajax-xhrio-handler
                            #(dispatch (conj on-success %))
                            #(dispatch (conj on-failure %))
                            api))
        (dissoc :on-success :on-failure))))

(defn http-effect
  [request]
  (let [seq-request-maps (if (sequential? request) request [request])]
    (doseq [request seq-request-maps]
      (-> request request->xhrio-options
          ajax/ajax-request))))

(reg-fx :http-xhrio http-effect)

(defn GET
  "returns a map formatted for re-frame's handling of XHR requests
  Has option to keywordize map keys or not (default true.)"
  [uri on-success on-failure & {:keys [keywords?] :or {keywords? true}}]
   {:method :get
    :uri uri
    :format (ajax/json-request-format)
    :response-format (ajax/json-response-format {:keywords? keywords?})
    :on-success [on-success]
    :on-failure [on-failure]})

(defn POST
  "Makes a post request"
  [uri data on-success on-failure]
  {:method          :post
   :uri             uri
   :params          data
   :format          (ajax/json-request-format)
   :response-format (ajax/json-response-format {:keywords? true})
   :on-success      [on-success]
   :on-failure      [on-failure]})

(defn PATCH
  "Makes a PATCH request"
  [uri data on-success on-failure]
  {:method          :PATCH
   :uri             uri
   :params          data
   :format          (ajax/json-request-format)
   :response-format (ajax/json-response-format {:keywords? true})
   :on-success      [on-success]
   :on-failure      [on-failure]})

(defn PUT
  "Makes a PUT request"
  [uri data on-success on-failure]
  {:method          :PUT
   :uri             uri
   :params          data
   :format          (ajax/json-request-format)
   :response-format (ajax/json-response-format {:keywords? true})
   :on-success      [on-success]
   :on-failure      [on-failure]})

(defn GET-FETCH
  "Make GET requests using the fetch web api.
  Originally written because og GET^ will not work on android for RN.
  This does NOT hook into a re-frame interceptor. (ie, http-xhrio)
  Links:
  https://github.com/JulianBirch/cljs-ajax/issues/141
  https://github.com/JulianBirch/cljs-ajax/issues/155"
  [uri on-success on-failure]
  (-> (js/fetch uri (clj->js {:method "GET"}))
      (.then #(.json %))
      (.then #(js->clj % :keywordize-keys true))
      (.then on-success)
      (.catch on-failure)
  ))


;; Error handler (especially for front end re-frame)

(defn handle-response
  "Handles errors+error types, otherwise success"
  [response {:keys [expected-response
                    on-success
                    on-spec-failure]}]
  (cond
    ;; If we don't have a spec for the response yet.
    (nil? expected-response)
    (on-success)

    ;; Spec fails - we pass validation errors from backend to on-spec-failure
    (not (spec/valid? expected-response response))
    (on-spec-failure)

    (spec/valid? expected-response response)
    (on-success)

    ))
