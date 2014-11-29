
(defproject reagent "0.4.4-SNAPSHOT"
  :url "http://github.com/reagent-project/reagent"
  :license {:name "MIT"}
  :description "A simple ClojureScript interface to React"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2342"]]
  :plugins [[lein-cljsbuild "1.0.3"]]
  :resource-paths ["vendor"]
  :source-paths ["src"]
  
  :profiles {:dev-base {:dependencies
                        [[figwheel "0.1.5-SNAPSHOT"]]
                        :plugins [[lein-figwheel "0.1.5-SNAPSHOT"]]
                        :resource-paths ["site" "outsite"]
                        :figwheel {:css-dirs ["site/public/css"]}
                        :cljsbuild {:builds
                                    {:client
                                     {:source-paths ["env/dev"]
                                      :compiler {:source-map true
                                                 :optimizations :none
                                                 :output-dir
                                                 "outsite/public/js/out"}}}}}
             
             :site {:resource-paths ^:replace ["outsite"]
                    :figwheel {:css-dirs ^:replace ["outsite/public/css"]}
                    :cljsbuild {:builds
                                {:client
                                 {:notify-command
                                  ["node" "bin/gen-site.js"]}}}}
             
             :prod [:base :site
                    {:cljsbuild {:builds
                                 {:client
                                  {:source-paths ["env/prod"]
                                   :compiler {:optimizations :advanced
                                              :elide-asserts true
                                              :output-dir "target/client"}}}}}]
             
             :test {:dependencies [[com.cemerick/clojurescript.test "0.3.1"]]
                    :cljsbuild {:builds
                                {:client {:source-paths ["test"]}}}}
             
             :dev [:dev-base :test]
             :prod-test [:prod :test]}

  :clean-targets ^{:protect false} [:target-path :compile-path
                                    "outsite/public/js"
                                    "outsite/public/site"
                                    "outsite/public/news"
                                    "outsite/public/index.html"
                                    "out"]

  :cljsbuild {:builds
              {:client {:source-paths ["src"
                                       "demo"
                                       "examples/todomvc/src"
                                       "examples/simple/src"
                                       "examples/geometry/src"]
                        :compiler
                        {:output-to "outsite/public/js/main.js"}}}}
  
  :figwheel {:http-server-root "public" ;; assumes "resources"
             :server-port 3449}

  ;-------------------
  
  ;; :profiles {:dev {:source-paths ["src" "demo"]}
  ;;            :prod {:cljsbuild
  ;;                   {:builds
  ;;                    {:client {:compiler
  ;;                              {:optimizations :advanced
  ;;                               :elide-asserts true
  ;;                               :pretty-print false}}}}}
  ;;            :test {:cljsbuild
  ;;                   {:builds
  ;;                    {:client {:source-paths ^:replace
  ;;                              ["test" "src" "demo"
  ;;                               "examples/todomvc/src"
  ;;                               "examples/simple/src"
  ;;                               "examples/geometry/src"]}}}}
  ;;            :srcmap {:cljsbuild
  ;;                     {:builds
  ;;                      {:client
  ;;                       {:compiler
  ;;                        {:source-map "target/cljs-client.js.map"
  ;;                         :source-map-path "client"}}}}}}
  ;; :cljsbuild
  ;; {:builds
  ;;  {:client {:source-paths ["src" "demo" "examples/todomvc/src"
  ;;                           "examples/simple/src"
  ;;                           "examples/geometry/src"]
  ;;            :notify-command ["node" "./bin/gen-site.js"]
  ;;            :compiler
  ;;            {:output-dir "target/client"
  ;;             :output-to "target/cljs-client.js"
  ;;             :pretty-print true}}}}
  )
