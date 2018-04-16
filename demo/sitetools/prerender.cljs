(ns sitetools.prerender
  (:require [reagent.debug :refer-macros [log]]
            [sitetools.core :as tools]
            [sitetools.server :as server]
            [reagentdemo.core :as demo]
            [path :as path]))

(defn -main [& args]
  (log "Generating site")
  (demo/init!)
  (let [[js-resource-path] args
        {:keys [site-dir pages] :as conf} (assoc @tools/config :js-resource-path js-resource-path)]
    (server/write-resources site-dir conf)
    (doseq [f (keys pages)]
      (server/write-file (->> f tools/to-relative (path/join site-dir))
                         (server/gen-page f conf))))
  (log "Wrote site")
  (js/process.exit 0))

(set! *main-cli-fn* -main)
