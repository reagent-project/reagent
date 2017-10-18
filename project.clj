(defproject reagent "0.8.0-alpha1"
  :url "http://github.com/reagent-project/reagent"
  :license {:name "MIT"}
  :description "A simple ClojureScript interface to React"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.908"]
                 ;; If :npm-deps enabled, these are used only for externs.
                 ;; Without direct react dependency, other packages,
                 ;; like react-leaflet might have closer dependency to a other version.
                 [cljsjs/react "16.0.0-0"]
                 [cljsjs/react-dom "16.0.0-0"]
                 [cljsjs/react-dom-server "16.0.0-0"]
                 [cljsjs/create-react-class "15.6.2-0"]]

  :plugins [[lein-cljsbuild "1.1.7"]
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

             :fig [{:dependencies [[figwheel "0.5.13"]]
                    :plugins [[lein-figwheel "0.5.13"]]
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
                                           :output-dir "target/client"}}}}}]

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
                                   ;; Add process.env.NODE_ENV preload
                                   :process-shim true
                                   :npm-deps {:react "16.0.0"
                                              :react-dom "16.0.0"
                                              :create-react-class "15.6.2"}}}}}

  :figwheel {:http-server-root "public" ;; assumes "resources"
             :repl false})
