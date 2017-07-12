(defproject reagent "0.8.0-SNAPSHOT"
  :url "http://github.com/reagent-project/reagent"
  :license {:name "MIT"}
  :description "A simple ClojureScript interface to React"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.762"]
                 ; [cljsjs/react-dom "15.6.1-1-SNAPSHOT"]
                 ; [cljsjs/react-dom-server "15.6.1-1-SNAPSHOT"]
                 ; [cljsjs/create-react-class "15.6.0-1-SNAPSHOT"]
                 ]

  :plugins [[lein-cljsbuild "1.1.6"]
            [lein-codox "0.10.3"]]

  :source-paths ["src"]

  :codox {:language :clojurescript
          :exclude clojure.string
          :source-paths ["src"]}

  :profiles {:test {:cljsbuild
                    {:builds {:client {:source-paths ["test"]
                                       :notify-command ["node" "bin/gen-site.js"]
                                       :compiler
                                       {:main "reagenttest.runtests"}}}}}

             :fig [{:dependencies [[figwheel "0.5.11"]]
                    :plugins [[lein-figwheel "0.5.11"]]
                    :source-paths ["demo"] ;; for lighttable
                    :resource-paths ["site" "outsite"]
                    :figwheel {:css-dirs ["site/public/css"]}
                    :cljsbuild
                    {:builds
                     {:client
                      {:figwheel true
                       :compiler {:source-map true
                                  :optimizations :none
                                  ;; :recompile-dependents false
                                  :output-dir "outsite/public/js/out"
                                  :asset-path "js/out"}}}}}]

             :site {:resource-paths ^:replace ["outsite"]
                    :figwheel {:css-dirs ^:replace ["outsite/public/css"]}}

             :prod [:site
                    {:cljsbuild
                     {:builds {:client
                               {:compiler {:optimizations :advanced
                                           :elide-asserts true
                                           :pretty-print false
                                           ;; :pseudo-names true
                                           :output-dir "target/client"
                                           ;; enables React production build - for npm-deps
                                           :closure-defines {"process.env.NODE_ENV" "production"}}}}}}]

             :prerender [:prod
                         {:cljsbuild
                          {:builds {:client
                                    {:compiler {:main "reagentdemo.server"
                                                :output-to "pre-render/main.js"
                                                :output-dir "pre-render/out"}
                                     :notify-command ["node" "bin/gen-site.js"] }}}}]

             :webpack {:cljsbuild
                       {:builds {:client
                                 {:compiler
                                  {:foreign-libs
                                   [{:file "target/webpack/bundle.js"
                                     :file-min "target/webpack/bundle.min.js"
                                     :provides ["cljsjs.react.dom"
                                                "cljsjs.react.dom.server"
                                                "cljsjs.react"]
                                     :requires []}]}}}}}

             :prod-test [:prod :test]

             :dev [:fig :test]

             :dev-notest [:fig]}

  :clean-targets ^{:protect false} [:target-path :compile-path
                                    "outsite/public/js"
                                    "outsite/public/site"
                                    "outsite/public/news"
                                    "outsite/public/css"
                                    "outsite/public/index.html"
                                    "out"
                                    "pre-render"]

  :cljsbuild {:builds {:client
                       {:source-paths ["src"
                                       "demo"
                                       "examples/todomvc/src"
                                       "examples/simple/src"
                                       "examples/geometry/src"]
                        :compiler {:parallel-build true
                                   :main "reagentdemo.core"
                                   :output-to "outsite/public/js/main.js"
                                   :language-in :ecmascript6
                                   :language-out :ecmascript3
                                   :closure-warnings {:non-standard-jsdoc :off}
                                   :preloads [process.env]
                                   :externs ["src/react.ext.js"
                                             "src/react-dom.ext.js"
                                             "src/react-dom-server.ext.js"
                                             "src/extra.js"]
                                   :npm-deps {:react "15.6.1"
                                              :react-dom "15.6.1"
                                              :create-react-class "15.5.3"}}}}}

  :figwheel {:http-server-root "public" ;; assumes "resources"
             :repl false})
