(ns sitetools.core
  (:require [clojure.string :as string]
            [goog.events :as evt]
            [goog.history.EventType :as hevt]
            [reagent.core :as r]
            [reagent.debug :refer-macros [dbg log dev?]]
            [reagent.interop :as i :refer-macros [.' .!]])
  (:import [goog History]
           [goog.history Html5History]
           [goog.net Jsonp]))

(when (exists? js/console)
  (enable-console-print!))

(declare page-content)


;;; Configuration

(defonce config (r/atom {:page-map {"index.html" [:div "Empty"]}
                         :page-titles {}
                         :body [page-content]
                         :site-dir "outsite/public"
                         :css-infiles ["site/public/css/main.css"]
                         :css-file "css/built.css"
                         :js-file "js/main.js"
                         :js-dir "js/out"
                         :main-div "main-content"
                         :allow-html5-history false}))

(defonce page (r/atom "index.html"))
(defonce page-state (r/atom {:has-history false}))

(defn register-page
  ([pageurl comp]
   (register-page pageurl comp nil))
  ([pageurl comp title]
   (assert (string? pageurl)
           (str "expected string, not " pageurl))
   (assert (vector? comp)
           (str "expected vector, not " (pr-str comp)))
   (assert (or (nil? title)
               (string? title)))
   (swap! config update-in [:page-map] assoc pageurl comp)
   (swap! config update-in [:page-titles] assoc pageurl title)
   pageurl))


;;; Components

(defn link
  [props child]
  (let [p (:href props)
        f ((:page-map @config) p)]
    (assert (vector? f) (str "couldn't resolve page " p))
    (assert (string? p))
    [:a (assoc props
          :href p
          :on-click (if (:has-history @page-state)
                      (fn [e]
                        (.preventDefault e)
                        (reset! page p)
                        (r/next-tick
                         #(set! (.-scrollTop (.-body js/document))
                                0)))
                      identity))
     child]))

(defn page-content []
  (get-in @config [:page-map @page]
          (get-in @config [:page-map "index.html"])))



;;; Implementation:

(defn get-title []
  (get-in @config [:page-titles @page]
          (or (get-in @config [:page-titles "index.html"])
              "")))

(defn default-content []
  [:div "Empty"])

(add-watch page ::title-watch
           (fn [_ _ _ p]
             (when r/is-client
               (set! (.-title js/document) (get-title)))
             ;; First title on a page wins
             #_(reset! page-title "")))

;;; History

(defn use-html5-history []
  (when r/is-client
    (let [proto (.' js/window :location.protocol)]
      (and (:allow-html5-history @config)
           (.isSupported Html5History)
           (#{"http:" "https:"} proto)))))

(defn create-history [p]
  (if (use-html5-history)
    (doto (Html5History.)
      (.setUseFragment false))
    (let [h (History.)]
      (when p
        (.setToken h p))
      h)))

(defonce history nil)

(defn token-base []
  (if (use-html5-history)
    (:base-path @config)))

(defn setup-history [p]
  (when (nil? history)
    (set! history (create-history p))
    (swap! page-state assoc :has-history (some? history))
    (when-let [h history]
      (evt/listen h hevt/NAVIGATE
                  (fn [e]
                    (let [t (.-token e)
                          tb (token-base)]
                      (reset! page (if (and tb (== 0 (.indexOf t tb)))
                                     (subs t (count tb))
                                     t)))
                    (r/flush)))
      (add-watch page ::history
                 (fn [_ _ oldp newp]
                   (when-not (= oldp newp)
                     (.setToken h (str (token-base) newp)))))
      (.setEnabled h true))))

(defn base-path [loc p]
  ;; Find base-path for html5 history
  (let [split #".[^/]*"
        depth (->> (case p "" "." p) (re-seq split) count)
        base (->> loc (re-seq split) (drop-last depth) (apply str))]
    (string/replace (str base "/") #"^/" "")))

(defn set-start-page [p]
  (when (and (not (:base-path @config))
             (use-html5-history))
    (swap! config assoc :base-path
           (base-path (.' js/window -location.pathname) p)))
  (reset! page p))

(defn prefix [href]
  (let [depth (-> #"/" (re-seq @page) count)]
    (str (->> "../" (repeat depth) (apply str)) href)))


;;; Static site generation

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
       [:base {:href (prefix "")}]
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
  (reset! page page-name)
  (let [b (r/render-component-to-string (body))]
    (str "<!doctype html>"
         (html-template {:title (get-title)
                         :body b
                         :page-conf {:allow-html5-history true
                                     :page-name page-name}
                         :timestamp timestamp}))))

(defn mkdirs [f]
  (let [fs (js/require "fs")
        path (js/require "path")
        items (as-> f _
                    (.' path dirname _)
                    (.' path normalize _)
                    (string/split _ #"/"))
        parts (reductions #(str %1 "/" %2) items)]
    (doseq [d parts]
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
  (clojure.string/join "\n"
                       (map read-file (:css-infiles @config))))

(defn write-resources [dir]
  (write-file (path-join dir (:css-file @config))
              (read-css)))


;;; Main entry points

(defn ^:export genpages [opts]
  (log "Generating site")
  (swap! config merge (js->clj opts :keywordize-keys true))
  (let [dir (:site-dir @config)
        timestamp (str "?" (.' js/Date now))]
    (doseq [f (keys (:page-map @config))]
      (write-file (path-join dir f)
                  (gen-page f timestamp)))
    (write-resources dir))
  (log "Wrote site"))

(defn start! [site-config]
  (swap! config merge site-config)
  (when r/is-client
    (let [conf (when (exists? js/pageConfig)
                 (js->clj js/pageConfig :keywordize-keys true))
          page-name (:page-name conf)]
      (swap! config merge conf)
      (when (nil? history)
        (when page-name
          (set-start-page page-name))
        (setup-history page-name)
        (set! (.-title js/document) (get-title)))
      (r/render-component (body)
                          (.' js/document getElementById
                              (:main-div @config))))))
