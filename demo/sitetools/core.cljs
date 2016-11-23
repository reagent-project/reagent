(ns sitetools.core
  (:require [clojure.string :as string]
            [goog.events :as evt]
            [reagent.core :as r]
            [reagent.dom.server :as server]
            [reagent.debug :refer-macros [dbg log dev?]]
            [reagent.interop :as i :refer-macros [$ $!]])
  (:import goog.History
           [goog.history Html5History EventType]))

(enable-console-print!)

;;; Configuration

(declare main-content)

(defonce config (r/atom {:body [#'main-content]
                         :pages {"/index.html" {:content [:div]
                                                :title ""}}
                         :site-dir "outsite/public"
                         :css-infiles ["site/public/css/main.css"]
                         :css-file "css/built.css"
                         :js-file "js/main.js"
                         :main-div "main-content"
                         :default-title ""}))

(defonce history nil)

(defn demo-handler [state [id x :as event]]
  (case id
    :set-content (let [page x
                       title (:title page)
                       title (if title
                               (str (:title-prefix state) title)
                               (str (:default-title state)))]
                   (when r/is-client
                     (set! js/document.title title))
                   (assoc state :current-page page :title title))
    :set-page (let [path x
                    _ (assert (string? path))
                    ps (:pages state)
                    p (get ps path (get ps "/index.html"))]
                (recur state [:set-content p]))
    :goto-page (let [path x
                     _ (assert (string? path))]
                 (when-some [h history]
                   (r/after-render (fn []
                                     (.setToken h x)
                                     (js/scrollTo 0 0)))
                   state)
                 (recur state [:set-page x]))))

(defn emit [event]
  ;; (dbg event)
  (r/rswap! config demo-handler event))

(defn register-page [url comp title]
  {:pre [(re-matches #"/.*[.]html" url)
         (vector? comp)]}
  (swap! config update-in [:pages]
         assoc url {:content comp :title title}))


;;; History

(defn init-history [page]
  (when-not history
    (let [html5 (and page
                     (Html5History.isSupported)
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
        (evt/listen EventType.NAVIGATE #(when (.-isNavigation %)
                                          (emit [:set-page (.-token %)])
                                          (r/flush)))
        (.setEnabled true))
      (let [token (.getToken history)
            p (if (and page (not html5) (empty? token))
                page
                token)]
        (emit [:set-page p])))))

(defn to-relative [f]
  (string/replace f #"^/" ""))


;;; Components

(defn link [props child]
  [:a (assoc props
             :href (-> props :href to-relative)
             :on-click #(do (.preventDefault %)
                            (emit [:goto-page (:href props)])))
   child])

(defn main-content []
  (get-in @config [:current-page :content]))


;;; Static site generation

(defn base [page]
  (let [depth (->> page to-relative (re-seq #"/") count)]
    (->> "../" (repeat depth) (apply str))))

(defn danger [t s]
  [t {:dangerouslySetInnerHTML {:__html s}}])

(defn html-template [{:keys [title body-html timestamp page-conf
                             js-file css-file main-div]}]
  (let [main (str js-file timestamp)]
    (server/render-to-static-markup
     [:html
      [:head
       [:meta {:charset 'utf-8}]
       [:meta {:name 'viewport
               :content "width=device-width, initial-scale=1.0"}]
       [:base {:href (-> page-conf :page-path base)}]
       [:link {:href (str css-file timestamp) :rel 'stylesheet}]
       [:title title]]
      [:body
       [:div {:id main-div} (danger :div body-html)]
       (danger :script (str "var pageConfig = "
                            (-> page-conf clj->js js/JSON.stringify)))
       [:script {:src main :type "text/javascript"}]]])))

(defn gen-page [page-path conf]
  (emit [:set-page page-path])
  (let [conf (merge conf @config)
        b (:body conf)
        bhtml (server/render-to-string b)]
    (str "<!doctype html>\n"
         (html-template (assoc conf
                               :page-conf {:page-path page-path}
                               :body-html bhtml)))))

(defn fs [] (js/require "fs"))
(defn path [] (js/require "path"))

(defn mkdirs [f]
  (doseq [d (reductions #(str %1 "/" %2)
                        (-> ($ (path) normalize f)
                            (string/split #"/")))]
    (when-not ($ (fs) existsSync d)
      ($ (fs) mkdirSync d))))

(defn write-file [f content]
  (mkdirs ($ (path) dirname f))
  ($ (fs) writeFileSync f content))

(defn path-join [& paths]
  (apply ($ (path) :join) paths))

(defn write-resources [dir {:keys [css-file css-infiles]}]
  (write-file (path-join dir css-file)
              (->> css-infiles
                   (map #($ (fs) readFileSync %))
                   (string/join "\n"))))


;;; Main entry points

(defn ^:export genpages [opts]
  (log "Generating site")
  (let [conf (swap! config merge (js->clj opts :keywordize-keys true))
        conf (assoc conf :timestamp (str "?" (js/Date.now)))
        {:keys [site-dir pages]} conf]
    (doseq [f (keys pages)]
      (write-file (->> f to-relative (path-join site-dir))
                  (gen-page f conf)))
    (write-resources site-dir conf))
  (log "Wrote site"))

(defn start! [site-config]
  (swap! config merge site-config)
  (when r/is-client
    (let [page-conf (when (exists? js/pageConfig)
                      (js->clj js/pageConfig :keywordize-keys true))
          conf (swap! config merge page-conf)
          {:keys [page-path body main-div]} conf]
      (init-history page-path)
      (r/render-component body (js/document.getElementById main-div)))))
