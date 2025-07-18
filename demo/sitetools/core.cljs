(ns sitetools.core
  (:require [clojure.string :as string]
            [goog.events :as evt]
            ["react" :as react]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [reagent.dom.client :as rdomc])
  (:import goog.History
           [goog.history Html5History EventType]))

;;; Configuration

(declare main-content)

(defonce root
  ;; Init only on use, this ns is loaded for SSR build also
  (delay (rdomc/create-root (js/document.getElementById "main-content"))))

(defonce config (r/atom {:body [#'main-content]
                         :pages {"/index.html" {:content [:div]
                                                :title ""}}
                         :site-dir "target/prerender/public/"
                         :css-infiles ["site/public/css/main.css"]
                         :css-file "css/built.css"
                         :js-file "js/main.js"
                         :react-root root
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
                     (#{"http:" "https:"} js/location.protocol))
          h (if html5
              (doto (Html5History.)
                (.setUseFragment false)
                (.setPathPrefix (-> js/location.pathname
                                    (string/replace
                                      (re-pattern (str page "$")) "")
                                    (string/replace #"/*$" ""))))
              (History.))]
      (set! history h)
      (evt/listen history EventType.NAVIGATE
                  (fn [^js/Event e]
                    (when (.-isNavigation e)
                      (emit [:set-page (.-token e)])
                      (r/flush))))
      (.setEnabled history true)
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

;;; Main entry points

(defn start! [site-config]
  (swap! config merge site-config)
  (when r/is-client
    (let [page-conf (when (exists? js/pageConfig)
                      (js->clj js/pageConfig :keywordize-keys true))
          conf (swap! config merge page-conf)
          {:keys [page-path body react-root]} conf]
      (init-history page-path)
      ;; Enable StrictMode to warn about e.g. findDOMNode
      ;; NOTE: timer-component will go forward 2 seconds at a time, because the
      ;; render fn will be called twice and it will register two timeouts.
      ; (rdomc/render @react-root [:> react/StrictMode {} body])
      (rdomc/render @react-root body))))
