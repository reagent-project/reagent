(ns site-preview
  "Prerendered site preview from the dev build, for checking the static HTML
  and hydration locally. Needs `shadow-cljs watch client` running (which also
  makes each build fast, ~2s).

    bb site-preview              build once into target/site-preview
    bb site-preview-watch [port] build, serve (default port 8095) and rebuild
                                 when sources, CSS, docs or the client build
                                 change"
  (:require [babashka.fs :as fs]
            [babashka.http-server :as http-server]
            [babashka.process :refer [shell]]))

(def out-dir "target/site-preview")

(def watch-dirs ["src" "demo" "prerender" "doc" "site/public/css"
                 "target/shadow-cljs/client/public/js"])

(defn build []
  ;; reagentdemo.docs is ^:dev/always, so the guides are always current.
  (shell "npx shadow-cljs compile prerender")
  (shell "node target/shadow-cljs-prerender/main.js target/shadow-cljs/client/public/")
  (fs/delete-tree out-dir)
  (fs/copy-tree "target/prerender/public" out-dir)
  (fs/create-sym-link (fs/path out-dir "js")
                      (fs/relativize (fs/absolutize out-dir)
                                     (fs/absolutize "target/shadow-cljs/client/public/js"))))

(defn- try-build []
  (println "[site-preview] building...")
  (try
    (build)
    (println "[site-preview] done")
    (catch Exception e
      (println "[site-preview] build failed:" (ex-message e)))))

(defn- changed-since [^long t]
  (into []
        (comp (filter fs/exists?)
              (mapcat #(fs/glob % "**" {:hidden false}))
              (filter #(and (fs/regular-file? %)
                            (> (.toMillis (fs/last-modified-time %)) t))))
        watch-dirs))

(defn watch [& [port]]
  (let [port (parse-long (or port "8095"))]
    (try-build)
    (http-server/serve {:port port :dir out-dir})
    (println (str "[site-preview] serving http://localhost:" port))
    (loop [since (System/currentTimeMillis)]
      (Thread/sleep 1000)
      (let [now (System/currentTimeMillis)
            changed (changed-since since)]
        (if (empty? changed)
          (recur now)
          (do
            ;; Let a burst of saves (or the client watch build) settle.
            (loop []
              (Thread/sleep 1000)
              (when (seq (changed-since (- (System/currentTimeMillis) 1000)))
                (recur)))
            ;; The guides are read by macros in reagentdemo.docs; touching it
            ;; makes the client watch recompile them too.
            (when (some #(fs/starts-with? % "doc") changed)
              (fs/set-last-modified-time "demo/reagentdemo/docs.cljs"
                                         (fs/instant->file-time (java.time.Instant/now))))
            (let [start (System/currentTimeMillis)]
              (try-build)
              (recur start))))))))
