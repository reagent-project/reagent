(defproject material-ui-reagent "0.6.0"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.339"]
                 [reagent "0.8.1"]
                 [re-frame "0.9.4"]
                 [secretary "1.2.3"]
                 [cljs-ajax "0.7.3"]
                 [figwheel "0.5.16"]
                 [cljsjs/material-ui "1.2.1-1"]
                 [cljsjs/material-ui-icons "1.1.0-1"]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.16"]]

  :figwheel {:repl false
             :http-server-root "public"
             :server-port 5000}

  :profiles {:dev {:resource-paths ["target/cljsbuild/client" "target/cljsbuild/client-npm"]}}

  :cljsbuild
  {:builds
   {:client
    {:source-paths ["src"]
     :figwheel true
     :compiler {:parallel-build true
                :source-map true
                :optimizations :none
                :main "example.core"
                :output-dir "target/cljsbuild/client/public/js/out"
                :output-to "target/cljsbuild/client/public/js/main.js"
                :asset-path "js/out"
                :npm-deps false}}

    ;; FIXME: Doesn't work due to Closure bug with scoped npm packages
    :client-npm
    {:source-paths ["src"]
     :figwheel true
     :compiler {:parallel-build true
                :source-map true
                :optimizations :none
                :main "example.core"
                :output-dir "target/cljsbuild/client-npm/public/js/out"
                :output-to "target/cljsbuild/client-npm/public/js/main.js"
                :asset-path "js/out"
                :install-deps true
                :npm-deps {react "16.4.0"
                           react-dom "16.4.0"
                           create-react-class "15.6.3"
                           "@material-ui/core" "1.2.1"
                           "@material-ui/icons" "1.1.0"}
                :process-shim true}}}})
