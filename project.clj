

(defproject cloact "0.0.1"
  :dependencies [[org.clojure/clojurescript "0.0-2120"]]
  :plugins [[lein-cljsbuild "1.0.1"]]
  :hooks [leiningen.cljsbuild]
  :profiles {:prod {:cljsbuild
                    {:builds
                     {:client {:compiler
                               {:optimizations :advanced
                                :pretty-print true}}}}}
             :test {:plugins [[com.cemerick/clojurescript.test "0.2.1"]]
                    :cljsbuild
                    {:builds
                     {:client {:source-paths ["test"
                                              "examples/todomvc/src"
                                              "examples/simple/src"]}
                      :server {:source-paths ["test"]}}}}
             :srcmap {:cljsbuild
                      {:builds
                       {:client
                        {:compiler
                         {:source-map "target/cljs-client.js.map"
                          :source-map-path "client"}}}}}}
  :source-paths ["src"]
  :cljsbuild
  {:builds
   {:server {:source-paths ["src"]
             :compiler
             {:output-dir "target/server"
              :output-to "target/cljs-server.js"
              :pretty-print true}}
    :client {:source-paths ["src"]
             :compiler
             {:output-dir "target/client"
              :output-to "target/cljs-client.js"
              :pretty-print true}}}})
