(ns sitetools.prerender
  (:require [reagent.debug :refer-macros [log]]
            [sitetools.core :as tools]
            [sitetools.server :as server]
            [reagentdemo.core :as demo]))

(defn -main [& args]
  (log "Generating site")
  (demo/init!)
  (let [conf @tools/config
        conf (assoc conf :timestamp (str "?" (js/Date.now)))
        {:keys [site-dir pages]} conf]
    (doseq [f (keys pages)]
      (server/write-file (->> f tools/to-relative (server/path-join site-dir))
                         (server/gen-page f conf)))
    (server/write-resources site-dir conf))
  (log "Wrote site")
  (js/process.exit 0))

(set! *main-cli-fn* -main)
