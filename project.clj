(defproject reagent "1.2.0"
  :url "http://github.com/reagent-project/reagent"
  :license {:name "MIT"}
  :description "A simple ClojureScript interface to React"

  :dependencies []

  :plugins [[lein-cljsbuild "1.1.8"]
            [lein-doo "0.1.11"]
            [lein-codox "0.10.8"]
            [lein-figwheel "0.5.20"]]

  :source-paths ["src"]

  :codox {:language :clojurescript
          :exclude clojure.string
          :source-paths ["src"]
          :doc-paths []}

  :profiles {:dev {:dependencies [[org.clojure/clojure "1.12.0"]
                                  [org.clojure/clojurescript "1.11.132"]
                                  [figwheel "0.5.20"]
                                  [figwheel-sidecar "0.5.20"]
                                  [doo "0.1.11"]
                                  [cljsjs/prop-types "15.8.1-0"]
                                  [funcool/promesa "11.0.678"]

                                  [cljsjs/react "18.3.1-1"]
                                  [cljsjs/react-dom "18.3.1-1"]
                                  [cljsjs/react-dom-server "18.3.1-1"]]
                   :source-paths ["demo" "test" "examples/todomvc/src" "examples/simple/src" "examples/geometry/src"]
                   :resource-paths ["site" "target/cljsbuild/client" "target/cljsbuild/client-npm"]}}

  :clean-targets ^{:protect false} [:target-path :compile-path "out"]

  :repl-options {:init (require '[figwheel-sidecar.repl-api :refer :all])}

  :figwheel {:http-server-root "public" ;; assumes "resources"
             :css-dirs ["site/public/css"]
             :repl true
             :nrepl-port 27397}

  :doo {:paths {:karma "npx karma"}}

  ;; No profiles and merging - just manual configuration for each build type.
  ;; For :optimization :none ClojureScript compiler will compile all
  ;; cljs files in source-paths. To ensure unncessary files
  ;; aren't compiled it would be better to not provide source-paths or
  ;; provide single file but currently this doesn't work for Cljsbuild.
  ;; In future :main alone should be enough to find entry file.
  :cljsbuild
  {:builds
   [{:id "client"
     :source-paths ["demo"]
     :watch-paths ["src" "demo" "test"]
     :figwheel true
     :compiler {:parallel-build true
                :optimizations :none
                :main "reagentdemo.dev"
                :output-dir "target/cljsbuild/client/public/js/out"
                :output-to "target/cljsbuild/client/public/js/main.js"
                :npm-deps false
                :asset-path "js/out"
                :checked-arrays :warn
                :infer-externs true}}

    {:id "client-npm"
     :source-paths ["demo"]
     :watch-paths ["src" "demo" "test"]
     :figwheel true
     :compiler {:parallel-build true
                :optimizations :none
                :main "reagentdemo.dev"
                :output-dir "target/cljsbuild/client-npm/public/js/out"
                :output-to "target/cljsbuild/client-npm/public/js/main.js"
                :npm-deps true
                :asset-path "js/out"
                :checked-arrays :warn
                :language-out :es5}}

    {:id "test"
     :source-paths ["test"]
     :compiler {:parallel-build true
                :optimizations :none
                :main "reagenttest.runtests"
                :asset-path "js/out"
                :output-dir "target/cljsbuild/test/out"
                :output-to "target/cljsbuild/test/main.js"
                :npm-deps false
                :aot-cache true
                :checked-arrays :warn
                :infer-externs true}}

    {:id "test-npm"
     :source-paths ["test"]
     :compiler {:parallel-build true
                :optimizations :none
                :main "reagenttest.runtests"
                :asset-path "js/out"
                :output-dir "target/cljsbuild/test-npm/out"
                :output-to "target/cljsbuild/test-npm/main.js"
                :npm-deps true
                :aot-cache true
                :checked-arrays :warn
                :language-out :es5}}

    ;; Separate source-path as this namespace uses Node built-in modules which
    ;; aren't available for other targets, and would break other builds.
    {:id "prerender"
     :source-paths ["prerender"]
     :compiler {:main "sitetools.prerender"
                :target :nodejs
                :output-dir "target/cljsbuild/prerender/out"
                :output-to "target/cljsbuild/prerender/main.js"
                :npm-deps true
                :aot-cache true}}

    {:id "node-test"
     :source-paths ["test"]
     :watch-paths ["src" "test"]
     :compiler {:main "reagenttest.runtests"
                :target :nodejs
                :parallel-build true
                :optimizations :none
                :output-dir "target/cljsbuild/node-test/out"
                :output-to "target/cljsbuild/node-test/main.js"
                :npm-deps false
                :aot-cache true
                :checked-arrays :warn}}

    {:id "node-test-npm"
     :source-paths ["test"]
     :watch-paths ["src" "test"]
     :compiler {:main "reagenttest.runtests"
                :target :nodejs
                :parallel-build true
                :optimizations :none
                :output-dir "target/cljsbuild/node-test-npm/out"
                :output-to "target/cljsbuild/node-test-npm/main.js"
                :npm-deps true
                :aot-cache true
                :checked-arrays :warn
                :closure-defines {"reagenttest.runtests.DOM_TESTS" false}}}

    ;; With :advanched source-paths doesn't matter that much as
    ;; Cljs compiler will only read :main file.
    {:id "prod"
     :source-paths ["demo"]
     :compiler {:main "reagentdemo.prod"
                :optimizations :advanced
                :elide-asserts true
                :pretty-print false
                ;; :pseudo-names true
                :stable-names true
                :output-to "target/cljsbuild/prod/public/js/main.js"
                :output-dir "target/cljsbuild/prod/out" ;; Outside of public, not published
                :npm-deps false
                :aot-cache true}}

    {:id "prod-npm"
     :source-paths ["demo"]
     :compiler {:main "reagentdemo.prod"
                :optimizations :advanced
                :elide-asserts true
                :pretty-print false
                :stable-names true
                :output-to "target/cljsbuild/prod-npm/public/js/main.js"
                :output-dir "target/cljsbuild/prod-npm/out" ;; Outside of public, not published
                :closure-warnings {:global-this :off}
                :npm-deps true
                :aot-cache true
                :language-out :es5}}

    {:id "prod-test"
     :source-paths ["test"]
     :compiler {:main "reagenttest.runtests"
                :optimizations :advanced
                :elide-asserts true
                :pretty-print false
                :output-to "target/cljsbuild/prod-test/main.js"
                :output-dir "target/cljsbuild/prod-test/out"
                :closure-warnings {:global-this :off}
                :npm-deps false
                :aot-cache true
                :checked-arrays :warn}}

    {:id "prod-test-npm"
     :source-paths ["test"]
     :compiler {:main "reagenttest.runtests"
                :optimizations :advanced
                :elide-asserts true
                :pretty-print false
                ;; :pseudo-names true
                :output-to "target/cljsbuild/prod-test-npm/main.js"
                :output-dir "target/cljsbuild/prod-test-npm/out"
                :closure-warnings {:global-this :off}
                :npm-deps true
                :aot-cache true
                :checked-arrays :warn
                :language-out :es5}}]})
