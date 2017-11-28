(defproject reagent "0.8.0-alpha2"
  :url "http://github.com/reagent-project/reagent"
  :license {:name "MIT"}
  :description "A simple ClojureScript interface to React"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.946"]
                 ;; If :npm-deps enabled, these are used only for externs.
                 ;; Without direct react dependency, other packages,
                 ;; like react-leaflet might have closer dependency to a other version.
                 [cljsjs/react "16.1.1-0"]
                 [cljsjs/react-dom "16.1.1-0"]
                 [cljsjs/react-dom-server "16.1.1-0"]
                 [cljsjs/create-react-class "15.6.2-0"]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-doo "0.1.8"]
            [lein-codox "0.10.3"]
            [lein-figwheel "0.5.14"]]

  :source-paths ["src"]

  :codox {:language :clojurescript
          :exclude clojure.string
          :source-paths ["src"]}

  :profiles {:dev {:dependencies [[figwheel "0.5.14"]
                                  [doo "0.1.8"]
                                  [cljsjs/prop-types "15.6.0-0"]]
                   :source-paths ["demo" "examples/todomvc/src" "examples/simple/src" "examples/geometry/src"]
                   :resource-paths ["site" "target/cljsbuild/client"]}}

  :clean-targets ^{:protect false} [:target-path :compile-path "out"]

  :figwheel {:http-server-root "public" ;; assumes "resources"
             :css-dirs ["site/public/css"]
             :repl false}

  ;; No profiles and merging - just manual configuration for each build type
  :cljsbuild
  {:builds
   {:client
    {:source-paths ["src" "demo" "test"]
     :figwheel true
     :compiler {:parallel-build true
                :source-map true
                :optimizations :none
                :main "reagentdemo.dev"
                :output-dir "target/cljsbuild/client/public/js/out"
                :output-to "target/cljsbuild/client/public/js/main.js"
                :asset-path "js/out"
                ;; add process.env.node_env preload
                :process-shim true}}

    :test
    {:source-paths ["src" "test"]
     :compiler {:parallel-build true
                :source-map true
                :optimizations :none
                :main "reagenttest.runtests"
                :asset-path "js/out"
                :output-dir "target/cljsbuild/test/out"
                :output-to "target/cljsbuild/test/main.js"
                ;; add process.env.node_env preload
                :process-shim true}}

    :prerender
    {:source-paths ["src" "demo"]
     :compiler {:main "sitetools.prerender"
                :target :nodejs
                :process-shim false
                :output-dir "target/cljsbuild/prerender/out"
                :output-to "target/cljsbuild/prerender/main.js"}}

    :node-test
    {:source-paths ["src" "test"]
     :compiler {:main "reagenttest.runtests"
                :target :nodejs
                :parallel-build true
                :source-map true
                :optimizations :none
                :output-dir "target/cljsbuild/node-test/out"
                :output-to "target/cljsbuild/node-test/main.js"}}

    :prod
    {:source-paths ["src" "demo"]
     :compiler {:main "reagentdemo.prod"
                :optimizations :advanced
                :elide-asserts true
                :pretty-print false
                ;; :pseudo-names true
                :output-to "target/cljsbuild/prod/public/js/main.js"
                :output-dir "target/cljsbuild/prod/out" ;; Outside of public, not published
                :closure-warnings {:global-this :off}}}

    :prod-test
    {:source-paths ["src" "demo"]
     :compiler {:main "reagenttest.runtests"
                :optimizations :advanced
                :elide-asserts true
                :pretty-print false
                ;; :pseudo-names true
                :output-to "target/cljsbuild/prod-test/main.js"
                :output-dir "target/cljsbuild/prod-test/out"
                :closure-warnings {:global-this :off}}}}})
