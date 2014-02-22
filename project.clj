
(defproject reagent "0.4.1"
  :url "http://github.com/holmsand/reagent"
  :license {:name "MIT"}
  :description "A simple ClojureScript interface to React"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2173"]]
  :plugins [[lein-cljsbuild "1.0.2"]
            [com.cemerick/clojurescript.test "0.2.2"]]
  :profiles {:prod {:cljsbuild
                    {:builds
                     {:client {:compiler
                               {:optimizations :advanced
                                :elide-asserts true
                                :preamble ^:replace ["reagent/react.min.js"]
                                :pretty-print false}}}}}
             :test {:cljsbuild
                    {:builds
                     {:client {:source-paths ^:replace
                               ["test" "src" "demo"
                                "examples/todomvc/src"
                                "examples/simple/src"
                                "examples/geometry/src"]}}}}
             :srcmap {:cljsbuild
                      {:builds
                       {:client
                        {:compiler
                         {:source-map "target/cljs-client.js.map"
                          :source-map-path "client"}}}}}}
  :source-paths ["src"]
  :resource-paths ["vendor"]
  :cljsbuild
  {:builds
   {:client {:source-paths ["src" "demo" "examples/todomvc/src"
                            "examples/simple/src"
                            "examples/geometry/src"]
             :notify-command ["node" "./bin/gen-site.js"]
             :compiler
             {:preamble ["reagent/react.js"]
              :output-dir "target/client"
              :output-to "target/cljs-client.js"
              :pretty-print true}}}})
