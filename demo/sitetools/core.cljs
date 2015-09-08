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

(defonce config (r/atom {:body [page-content]
                         :main-content [:div]
                         :pages #{}
                         :site-dir "outsite/public"
                         :css-infiles ["site/public/css/main.css"]
                         :css-file "css/built.css"
                         :js-file "js/main.js"
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

(defn to-relative [f]
  (string/replace f #"^/" ""))


;;; Components

(defn link [props child]
  [:a (assoc props
             :href (-> props :href to-relative)
             :on-click #(do (.preventDefault %)
                            (dispatch [:goto-page (:href props)])))
   child])

(defn page-content []
  (:main-content @config))



;;; Static site generation

(defn prefix [href page]
  (let [depth (-> #"/" (re-seq (to-relative page)) count)]
    (str (->> "../" (repeat depth) (apply str)) href)))

(defn danger [t s]
  [t {:dangerouslySetInnerHTML {:__html s}}])

(defn html-template [{:keys [title body-html timestamp page-conf
                             js-file css-file main-div]}]
  (let [main (str js-file timestamp)]
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
       [:div {:id main-div} (danger :div body-html)]
       (danger :script (str "var pageConfig = "
                            (-> page-conf clj->js js/JSON.stringify) ";"))
       [:script {:src main :type "text/javascript"}]]])))

(defn gen-page [page-name conf]
  (dispatch [:set-page page-name])
  (let [b (:body conf)
        _ (assert (vector? b))
        bhtml (r/render-component-to-string b)]
    (str "<!doctype html>"
         (html-template (assoc conf
                               :page-conf {:page-name page-name}
                               :body-html bhtml)))))

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
  (mkdirs f)
  (.' (js/require "fs") writeFileSync f content))

(defn read-file [f]
  (.' (js/require "fs") readFileSync f))

(defn path-join [& paths]
  (apply (.' (js/require "path") :join) paths))

(defn read-files [files]
  (string/join "\n" (map read-file files)))

(defn write-resources [dir {:keys [css-file css-infiles]}]
  (write-file (path-join dir css-file)
              (read-files css-infiles)))


;;; Main entry points

(defn ^:export genpages [opts]
  (log "Generating site")
  (swap! config merge (js->clj opts :keywordize-keys true))
  (let [{:keys [site-dir pages] :as state} @config
        conf (assoc state :timestamp (str "?" (js/Date.now)))]
    (doseq [f pages]
      (write-file (->> f to-relative (path-join site-dir))
                  (gen-page f conf)))
    (write-resources site-dir conf))
  (log "Wrote site"))

(defn start! [site-config]
  (swap! config merge site-config)
  (when r/is-client
    (let [conf (when (exists? js/pageConfig)
                 (js->clj js/pageConfig :keywordize-keys true))]
      (swap! config merge conf)
      (init-history)
      (r/render-component (:body @config)
                          (js/document.getElementById (:main-div @config))))))
