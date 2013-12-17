

(defproject simple-cloact "0.0.2-SNAPSHOT"
  :dependencies [[org.clojure/clojurescript "0.0-2120"]
                 [cloact "0.0.2-SNAPSHOT"]]
  :plugins [[lein-cljsbuild "1.0.0"]]
  :hooks [leiningen.cljsbuild]
  :profiles {:prod {:cljsbuild
                    {:builds
                     {:client {:compiler
                               {:optimizations :advanced
                                :pretty-print false}}}}}
             :srcmap {:cljsbuild
                      {:builds
                       {:client {:compiler
                                 {:source-map "target/client.js.map"
                                  :source-map-path "client"}}}}}}
  :source-paths ["src"]
  :cljsbuild
  {:builds
   {:client {:source-paths ["src"]
             :compiler
             {:output-dir "target/client"
              :output-to "target/client.js"
              :pretty-print true}}}})
