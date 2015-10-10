(defproject reagent "0.6.0-SNAPSHOT"
  :url "http://github.com/reagent-project/reagent"
  :license {:name "MIT"}
  :description "A simple ClojureScript interface to React"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.48"]
                 [cljsjs/react-dom "0.14.0-0"]]

  :plugins [[lein-cljsbuild "1.1.0"]
            [codox "0.8.12"]]

  :source-paths ["src"]

  :codox {:language :clojurescript
          :exclude clojure.string}

  :profiles {:test {:cljsbuild
                    {:builds {:client {:source-paths ["test"]}}}}

             :dev [:test
                   {:dependencies [[figwheel "0.4.0"]]
                    :plugins [[lein-figwheel "0.4.0"]]
                    :source-paths ["demo"] ;; for lighttable
                    :resource-paths ["site" "outsite"]
                    :figwheel {:css-dirs ["site/public/css"]}
                    :cljsbuild
                    {:builds
                     {:client
                      {:figwheel {:on-jsload "reagenttest.runtests/reload"}
                       :compiler {:main "reagenttest.runtests"
                                  :source-map true
                                  :source-map-timestamp true
                                  :optimizations :none
                                  :output-dir "outsite/public/js/out"
                                  :asset-path "js/out"}}}}}]
             
             :site {:resource-paths ^:replace ["outsite"]
                    :figwheel {:css-dirs ^:replace ["outsite/public/css"]}
                    :cljsbuild
                    {:builds {:client
                              {:notify-command ["node" "bin/gen-site.js"]}}}}
             
             :prod [:site
                    {:cljsbuild
                     {:builds {:client
                               {:compiler {:optimizations :advanced
                                           :elide-asserts true
                                           :pretty-print false
                                           ;; :pseudo-names true
                                           :output-dir "target/client"}}}}}]

             :webpack {:cljsbuild
                       {:builds {:client
                                 {:compiler
                                  {:foreign-libs
                                   [{:file "target/webpack/bundle.js"
                                     :file-min "target/webpack/bundle.min.js"
                                     :provides ["cljsjs.react.dom"
                                                "cljsjs.react"]}]}}}}}

             :prod-test [:test :prod]
             
             :dev-notest [:dev
                          {:cljsbuild
                           {:builds {:client
                                     {:compiler {:load-tests false}}}}}]}

  :clean-targets ^{:protect false} [:target-path :compile-path
                                    "outsite/public/js"
                                    "outsite/public/site"
                                    "outsite/public/news"
                                    "outsite/public/css"
                                    "outsite/public/index.html"
                                    "out"]

  :cljsbuild {:builds {:client
                       {:source-paths ["src"
                                       "demo"
                                       "examples/todomvc/src"
                                       "examples/simple/src"
                                       "examples/geometry/src"]
                        :compiler {:output-to "outsite/public/js/main.js"}}}}
  
  :figwheel {:http-server-root "public" ;; assumes "resources"
             :repl false})
