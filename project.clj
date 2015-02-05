
(defproject reagent "0.5.0-SNAPSHOT"
  :url "http://github.com/reagent-project/reagent"
  :license {:name "MIT"}
  :description "A simple ClojureScript interface to React"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2760"]
                 [cljsjs/react "0.12.2-5"]]
  :plugins [[lein-cljsbuild "1.0.4"]]
  :resource-paths ["vendor"]
  :source-paths ["src"]
  
  :profiles {:base {:cljsbuild {:builds
                                    {:client
                                     {:source-paths
                                      ["src"
                                       "demo"
                                       "examples/todomvc/src"
                                       "examples/simple/src"
                                       "examples/geometry/src"]}}}}

             :test {:cljsbuild {:builds
                                {:client {:source-paths ["test"]}}}}

             :dev-base {:dependencies
                        [[figwheel "0.2.2-SNAPSHOT"]]
                        :plugins [[lein-figwheel "0.2.2-SNAPSHOT"]]
                        :source-paths ["demo"] ;; for lighttable
                        :resource-paths ["site" "outsite"]
                        :figwheel {:css-dirs ["site/public/css"]}
                        :cljsbuild {:builds
                                    {:client
                                     {:source-paths ["env/dev"]
                                      :compiler {:main "devsetup"
                                                 :source-map true
                                                 :source-map-timestamp true
                                                 :optimizations :none
                                                 :output-dir
                                                 "outsite/public/js/out"}}}}}
             
             :site {:resource-paths ^:replace ["outsite" "vendor"]
                    :figwheel {:css-dirs ^:replace ["outsite/public/css"]}
                    :cljsbuild {:builds
                                {:client
                                 {:notify-command
                                  ["node" "bin/gen-site.js"]}}}}
             
             :prod [:base :site
                    {:cljsbuild {:builds
                                 {:client
                                  {:source-paths ["env/prod"]
                                   :compiler {:main "prodsetup"
                                              :optimizations :advanced
                                              :elide-asserts true
                                              :pretty-print false
                                              :output-dir "target/client"}}}}}]
             
             :dev [:test :base :dev-base]
             :prod-test [:test :prod]}

  :clean-targets ^{:protect false} [:target-path :compile-path
                                    "outsite/public/js"
                                    "outsite/public/site"
                                    "outsite/public/news"
                                    "outsite/public/index.html"
                                    "out"]

  :cljsbuild {:builds
              {:client {:compiler
                        {:output-to "outsite/public/js/main.js"
                         :asset-path "js/out"}}}}
  
  :figwheel {:http-server-root "public" ;; assumes "resources"
             :repl false
             :server-port 3449})
