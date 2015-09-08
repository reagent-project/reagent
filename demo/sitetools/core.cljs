(ns sitetools.core
  (:require [clojure.string :as string]
            [goog.events :as evt]
            [goog.history.EventType :as hevt]
            [reagent.core :as r]
            [secretary.core :as secretary :refer-macros [defroute]]
            [reagent.debug :refer-macros [dbg log dev?]]
            [reagent.interop :as i :refer-macros [.' .!]])
  (:import goog.History
           goog.history.Html5History))

(when (exists? js/console)
  (enable-console-print!))

(defn rswap! [a f & args]
  ;; Roughly like swap!, except that recursive swaps are ok
  (let [fs (or (.-rswapfs a)
               (set! (.-rswapfs a) (array)))]
    (.push fs #(apply f % args))
    (if (< 1 (alength fs))
      nil
      (let [f' (fn [state]
                 ;;;; TODO: This could throw
                 (let [s ((aget fs 0) state)]
                   (.shift fs)
                   (if (-> fs alength pos?)
                     (recur s)
                     s)))]
        (swap! a f')))))


;;; Configuration

(declare page-content)

(defonce config (r/atom {;;:page-map {"index.html" [:div "Empty"]}
                         ;;:page-titles {}
                         :body [page-content]
                         :main-content [:div]
                         :pages #{}
                         :site-dir "outsite/public"
                         :css-infiles ["site/public/css/main.css"]
                         :css-file "css/built.css"
                         :js-file "js/main.js"
                         :js-dir "js/out"
                         :main-div "main-content"
                         :default-title ""}))

(defonce history nil)

(defn demo-handler [state [id v1 v2 :as event]]
  (case id
    :content (do
               (let [title (if v2
                             (str (:title-prefix state) v2)
                             (str (:default-title state)))]
                 (when r/is-client
                   (r/next-tick #(set! js/document.title title)))
                 (assoc state
                        :main-content v1
                        :title title)))
    :set-page (do (secretary/dispatch! v1)
                  (assoc state :page-name v1))
    :goto-page (do
                 (when r/is-client
                   (.setToken history v1 false)
                   (r/next-tick #(set! js/document.body.scrollTop 0)))
                 (recur state [:set-page v1]))
    state))

(defn dispatch [event]
  ;; (dbg event)
  (rswap! config demo-handler event)
  nil)

(defn reg-page [url]
  (swap! config update-in [:pages] conj url))


;;; History

(defn init-history []
  (when-not history
    (let [page (:page-name @config)
          html5 (and page
                     (.isSupported Html5History)
                     (#{"http:" "https:"} js/location.protocol))]
      (doto (set! history
                  (if html5
                    (doto (Html5History.)
                      (.setUseFragment false)
                      (.setPathPrefix (-> js/location.pathname
                                          (string/replace
                                           (re-pattern (str page "$")) "")
                                          (string/replace #"/*$" ""))))
                    (History.)))
        (evt/listen hevt/NAVIGATE #(dispatch [:set-page (.-token %)]))
        (.setEnabled true))
      (when (and page (not html5) (empty? (.getToken history)))
        (dispatch [:set-page page])))))

(defn as-relative [f]
  (string/replace f #"^/" ""))


;;; Components

(defn link [props child]
  [:a (assoc props
             :href (-> props :href as-relative)
             :on-click #(do (.preventDefault %)
                            (dispatch [:goto-page (:href props)])))
   child])

(defn page-content []
  (:main-content @config))



;;; Static site generation

(defn prefix [href page]
  (let [depth (-> #"/" (re-seq (as-relative page)) count)]
    (str (->> "../" (repeat depth) (apply str)) href)))

(defn body []
  (let [b (:body @config)]
    (assert (vector? b) (str "body is not a vector: " b))
    b))

(defn danger [t s]
  [t {:dangerouslySetInnerHTML {:__html s}}])

(defn html-template [{:keys [title body timestamp page-conf
                             opt-none req]}]
  (let [c @config
        main (str (:js-file c) timestamp)
        css-file (:css-file c)
        opt-none (:opt-none c)]
    (r/render-to-static-markup
     [:html
      [:head
       [:meta {:charset "utf-8"}]
       [:meta {:name 'viewport
               :content "width=device-width, initial-scale=1.0"}]
       [:base {:href (prefix "" (:page-name page-conf))}]
       [:link {:href (str css-file timestamp) :rel 'stylesheet}]
       [:title title]]
      [:body
       [:div {:id (:main-div @config)}
        (danger :div body)]
       (danger :script (str "var pageConfig = " (-> page-conf
                                                    clj->js
                                                    js/JSON.stringify)))
       [:script {:src main :type "text/javascript"}]]])))

(defn gen-page [page-name timestamp]
  ;; (reset! page page-name)
  (dispatch [:set-page page-name])
  (let [b (r/render-component-to-string (body))]
    (str "<!doctype html>"
         (html-template {:title (:title @config)
                         :body b
                         :page-conf {:page-name page-name}
                         :timestamp timestamp}))))

(defn mkdirs [f]
  (let [fs (js/require "fs")
        path (js/require "path")
        items (as-> f _
                (.' path dirname _)
                (.' path normalize _)
                (string/split _ #"/"))]
    (doseq [d (reductions #(str %1 "/" %2) items)]
      (when-not (.' fs existsSync d)
        (.' fs mkdirSync d)))))

(defn write-file [f content]
  (let [fs (js/require "fs")]
    (mkdirs f)
    (.' fs writeFileSync f content)))

(defn read-file [f]
  (let [fs (js/require "fs")]
    (.' fs readFileSync f)))

(defn path-join [& paths]
  (let [path (js/require "path")]
    (apply (.' path :join) paths)))

(defn read-css []
  (string/join "\n" (map read-file (:css-infiles @config))))

(defn write-resources [dir]
  (write-file (path-join dir (:css-file @config))
              (read-css)))


;;; Main entry points

(defn ^:export genpages [opts]
  (log "Generating site")
  (swap! config merge (js->clj opts :keywordize-keys true))
  (let [dir (:site-dir @config)
        timestamp (str "?" (js/Date.now))]
    (doseq [f (:pages @config)]
      (write-file (path-join dir (dbg (as-relative f)))
                  (gen-page f timestamp)))
    (write-resources dir))
  (log "Wrote site"))

(defn start! [site-config]
  (swap! config merge site-config)
  (when r/is-client
    (let [conf (when (exists? js/pageConfig)
                 (js->clj js/pageConfig :keywordize-keys true))]
      (swap! config merge conf)
      (init-history)
      (r/render-component (body)
                          (js/document.getElementById (:main-div @config))))))
