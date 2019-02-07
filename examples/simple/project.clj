
(defproject simple-reagent "0.6.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.516"]
                 [reagent "0.8.1"]
                 [figwheel "0.5.18"]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.18"]]

  :hooks [leiningen.cljsbuild]

  :profiles {:dev {:cljsbuild
                   {:builds {:client
                             {:figwheel {:on-jsload "simpleexample.core/run"}
                              :compiler {:main "simpleexample.core"
                                         :optimizations :none}}}}}

             :prod {:cljsbuild
                    {:builds {:client
                              {:compiler {:optimizations :advanced
                                          :elide-asserts true
                                          :pretty-print false}}}}}}

  :figwheel {:repl false}

  :cljsbuild {:builds {:client
                       {:source-paths ["src"]
                        :compiler {:output-dir "target/client"
                                   :output-to "target/client.js"}}}})
