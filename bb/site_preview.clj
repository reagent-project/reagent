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
            [babashka.filewatcher :as fw]
            [babashka.process :refer [shell]])
  (:import [java.util.concurrent LinkedBlockingQueue TimeUnit]))

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

(def settle-ms
  "Rebuild once no change has arrived for this long, so a burst of saves (or
  the client watch build writing its output) causes one build."
  300)

(defn- take-changes
  "Waits for a change (forever, or up to `wait-ms`), then collects changes
  until settle-ms pass without one. Returns the changed paths."
  ([q] (take-changes q nil))
  ([^LinkedBlockingQueue q wait-ms]
   (loop [paths (if-some [p (if wait-ms
                              (.poll q wait-ms TimeUnit/MILLISECONDS)
                              (.take q))]
                  [p]
                  [])]
     (if-some [p (and (seq paths) (.poll q settle-ms TimeUnit/MILLISECONDS))]
       (recur (conj paths p))
       paths))))

(defn watch [& [port]]
  (let [port (parse-long (or port "8095"))
        changes (LinkedBlockingQueue.)]
    (try-build)
    (http-server/serve {:port port :dir out-dir})
    (fw/watch (filterv fs/exists? watch-dirs)
              (fn [{:keys [type path error]}]
                (case type
                  :ready nil
                  :error (println "[site-preview] watch error:" (ex-message error))
                  (.put changes path)))
              {:ignore-initial true})
    (println (str "[site-preview] serving http://localhost:" port))
    (loop []
      (let [changed (take-changes changes)]
        ;; The guides are read by macros in reagentdemo.docs; touching it
        ;; makes the client watch recompile them too.
        (when (some #(fs/starts-with? % "doc") changed)
          (fs/set-last-modified-time "demo/reagentdemo/docs.cljs"
                                     (fs/instant->file-time (java.time.Instant/now)))
          ;; Swallow the change event of the touch itself.
          (take-changes changes 1000))
        (try-build)
        (recur)))))
