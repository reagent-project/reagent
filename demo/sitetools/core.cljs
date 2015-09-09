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
  ;; Like swap!, except that recursive swaps on the same atom are ok,
  ;; and always returns nil.
  {:pre [(satisfies? ISwap a)
         (ifn? f)]}
  (if a.rswapping
    (-> (or a.rswapfs
            (set! a.rswapfs (array)))
        (.push #(apply f % args)))
    (do (set! a.rswapping true)
        (try (swap! a (fn [state]
                        (loop [s (apply f state args)]
                          (if-some [sf (some-> a.rswapfs .shift)]
                            (recur (sf s))
                            s))))
             (finally
               (set! a.rswapping false)))))
  nil)


;;; Configuration

(declare main-content)

(defonce config (r/atom {:body [#'main-content]
                         :main-content [:div]
                         :pages #{}
                         :site-dir "outsite/public"
                         :css-infiles ["site/public/css/main.css"]
                         :css-file "css/built.css"
                         :js-file "js/main.js"
                         :main-div "main-content"
                         :default-title ""}))

(defonce history nil)

(defn demo-handler [state [id x y :as event]]
  (case id
    :set-content (let [title (if y
                               (str (:title-prefix state) y)
                               (str (:default-title state)))]
                   (assert (vector? x))
                   (when r/is-client
                     (set! js/document.title title))
                   (assoc state :main-content x :title title))
    :set-page (do (assert (string? x))
                  (secretary/dispatch! x)
                  (assoc state :page-name x))
    :goto-page (do
                 (assert (string? x))
                 (if history
                   (do (.setToken history x)
                       (r/next-tick #(set! js/document.body.scrollTop 0))
                       state)
                   (recur state [:set-page x])))))

(defn dispatch [event]
  ;; (dbg event)
  (rswap! config demo-handler event))

(defn add-page-to-generate [url]
  {:pre [(string? url)]}
  (swap! config update-in [:pages] conj url))

(defn register-page [url comp title]
  {:pre [(re-matches #"/.*[.]html" url)
         (vector? comp)]}
  (secretary/add-route! url #(dispatch [:set-content comp title]))
  (add-page-to-generate url))


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
        (evt/listen hevt/NAVIGATE #(dispatch [:set-page (.-token %)]))
        (.setEnabled true))
      (when (and page (not html5) (-> history .getToken empty?))
        (.setToken history page)))))

(defn to-relative [f]
  (string/replace f #"^/" ""))


;;; Components

(defn link [props child]
  [:a (assoc props
             :href (-> props :href to-relative)
             :on-click #(do (.preventDefault %)
                            (dispatch [:goto-page (:href props)])))
   child])

(defn main-content []
  (let [{comp :main-content} @config]
    (assert (vector? comp))
    comp))


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

(defn fs [] (js/require "fs"))
(defn path [] (js/require "path"))

(defn mkdirs [f]
  (doseq [d (reductions #(str %1 "/" %2)
                        (-> (.' (path) normalize f)
                            (string/split #"/")))]
    (when-not (.' (fs) existsSync d)
      (.' (fs) mkdirSync d))))

(defn write-file [f content]
  (mkdirs (.' (path) dirname f))
  (.' (fs) writeFileSync f content))

(defn path-join [& paths]
  (apply (.' (path) :join) paths))

(defn write-resources [dir {:keys [css-file css-infiles]}]
  (write-file (path-join dir css-file)
              (->> css-infiles
                   (map #(.' (fs) readFileSync %))
                   (string/join "\n"))))


;;; Main entry points

(defn ^:export genpages [opts]
  (log "Generating site")
  (let [conf (swap! config merge (js->clj opts :keywordize-keys true))
        conf (assoc conf :timestamp (str "?" (js/Date.now)))
        {:keys [site-dir pages]} conf]
    (doseq [f pages]
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
          {:keys [page-name body main-div]} conf]
      (init-history page-name)
      (r/render-component body (js/document.getElementById main-div)))))
