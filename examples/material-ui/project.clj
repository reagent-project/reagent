(defproject material-ui-reagent "0.6.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.516"]
                 [reagent "0.8.1"]
                 [figwheel "0.5.18"]
                 [cljsjs/material-ui "3.9.1-0"]
                 [cljsjs/material-ui-icons "3.0.1-0"]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.18"]]

  :figwheel {:repl false
             :http-server-root "public"}

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
                :npm-deps {react "16.8.1"
                           react-dom "16.8.1"
                           "@material-ui/core" "3.1.1"
                           "@material-ui/icons" "3.0.1"}
                :process-shim true}}}})
