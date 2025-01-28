(ns sitetools.prerender
  (:require [reagentdemo.core :as demo]
            [clojure.string :as string]
            [goog.events :as evt]
            [reagent.core :as r]
            [reagent.dom.server :as server]
            [reagent.debug :refer [log]]
            [sitetools.core :as tools]

            ;; Node libs
            [path :as path]
            [md5-file :as md5-file]
            [path :as path]
            [fs :as fs]))

(defn base [page]
  (let [depth (->> page tools/to-relative (re-seq #"/") count)]
    (->> "../" (repeat depth) (apply str))))

(defn danger [t s]
  [t {:dangerouslySetInnerHTML {:__html s}}])

(defn add-cache-buster [resource-path path]
  (let [h (md5-file/sync (path/join resource-path path))]
    (str path "?" (subs h 0 6))))

(defn html-template [{:keys [title body-html page-conf
                             js-file css-file
                             js-resource-path site-dir]}]
  (server/render-to-static-markup
    [:html
     [:head
      [:meta {:charset 'utf-8}]
      [:meta {:name 'viewport
              :content "width=device-width, initial-scale=1.0"}]
      [:base {:href (-> page-conf :page-path base)}]
      [:link {:href (add-cache-buster site-dir css-file)
              :rel "stylesheet"}]
      [:title title]]
     [:body
      [:div
       {:id "main-content"}
       (danger :div body-html)]
      (danger :script (str "var pageConfig = "
                           (-> page-conf clj->js js/JSON.stringify)))
      [:script {:src (add-cache-buster js-resource-path js-file)
                :type "text/javascript"}]]]))

(defn gen-page [page-path conf]
  (tools/emit [:set-page page-path])
  (let [conf (merge conf @tools/config)
        b (:body conf)
        bhtml (server/render-to-string b)]
    (str "<!doctype html>\n"
         (html-template (assoc conf
                               :page-conf {:page-path page-path}
                               :body-html bhtml)))))

(defn mkdirs [f]
  (doseq [d (reductions #(str %1 "/" %2)
                        (-> (path/normalize f)
                            (string/split #"/")))]
    (when-not (fs/existsSync d)
      (fs/mkdirSync d))))

(defn write-file [f content]
  (log "Write" f)
  (mkdirs (path/dirname f))
  (fs/writeFileSync f content))

(defn write-resources [dir {:keys [css-file css-infiles]}]
  (write-file (path/join dir css-file)
              (->> css-infiles
                   (map #(fs/readFileSync %))
                   (string/join "\n"))))

(defn -main [& args]
  (log "Generating site")
  (demo/init!)
  (let [[js-resource-path] args
        {:keys [site-dir pages] :as conf} (assoc @tools/config :js-resource-path js-resource-path)]
    (write-resources site-dir conf)
    (doseq [f (keys pages)]
      (write-file (->> f tools/to-relative (path/join site-dir))
                         (gen-page f conf))))
  (log "Wrote site")
  (js/process.exit 0))

(set! *main-cli-fn* -main)
