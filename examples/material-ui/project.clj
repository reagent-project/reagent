(defproject material-ui-reagent "0.6.0"
  :min-lein-version "2.5.3"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.339"]
                 [reagent "0.8.1"]
                 [re-frame "0.9.4"]
                 [secretary "1.2.3"]
                 [cljs-ajax "0.7.3"]
                 [figwheel "0.5.16"]
                 [cljsjs/material-ui "1.2.1-1"]
                 [cljsjs/material-ui-icons "1.1.0-1"]
                 [compojure "1.6.0"]
                 [ring/ring-jetty-adapter "1.6.2"]
                 [ring/ring-ssl "0.3.0"]
                 [environ "1.1.0"]]

  :plugins [[environ/environ.lein "0.3.1"]
            [lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.16"]]

  :hooks [environ.leiningen.hooks]
  :uberjar-name "example.jar"
  :figwheel {:repl false
             :http-server-root "public"
             :server-port 5000}

             :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.2"]
                                             [figwheel-sidecar "0.5.13"]
                                             [binaryage/devtools "0.9.4"]]
                              :source-paths ["src" "dev"]
                              :cljsbuild {:builds [{:id "dev"
                                                    :source-paths ["src"]
                                                    :figwheel true
                                                    :compiler {:main "example.core"
                                                               :preloads [devtools.preload]
                                                               :asset-path "js/out"
                                                               :output-to "resources/public/js/main.js"
                                                               :output-dir "resources/public/js/out"
                                                               :optimizations :none
                                                               :recompile-dependents true
                                                               :source-map true}}]}}
                        :uberjar {:env {:production true}
                                  :source-paths ["src"]
                                  :prep-tasks ["compile" ["cljsbuild" "once"]]
                                  :cljsbuild {:builds [{:id "production"
                                                        :source-paths ["src"]
                                                        :jar true
                                                        :compiler {:main "example.core"
                                                                   :asset-path "js/out"
                                                                   :output-to "resources/public/js/main.js"
                                                                   :output-dir "resources/public/js/out"
                                                                   :optimizations :advanced
                                                                   :pretty-print false}}]}}})
