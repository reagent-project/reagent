
(defproject reagent "0.2.0"
  :url "http://github.com/holmsand/reagent"
  :license {:name "MIT"}
  :description "A simple ClojureScript interface to React"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2138"]]
  :plugins [[lein-cljsbuild "1.0.1"]
            [com.cemerick/clojurescript.test "0.2.1"]]
  :profiles {:prod {:cljsbuild
                    {:builds
                     {:client {:compiler
                               {:optimizations :advanced
                                :preamble ^:replace ["reagent/react.min.js"]
                                :pretty-print false}}}}}
             :test {:plugins [[com.cemerick/clojurescript.test "0.2.1"]]
                    :cljsbuild
                    {:builds
                     {:client {:source-paths ["test"]}}}}
             :srcmap {:cljsbuild
                      {:builds
                       {:client
                        {:compiler
                         {:source-map "target/cljs-client.js.map"
                          :source-map-path "client"}}}}}}
  :source-paths ["src"]
  :cljsbuild
  {:builds
   {:client {:source-paths ["src" "demo" "examples/todomvc/src"
                            "examples/simple/src"]
             :notify-command ["node" "./bin/gen-site.js"]
             :compiler
             {:preamble ["reagent/react.js"]
              :output-dir "target/client"
              :output-to "target/cljs-client.js"
              :pretty-print true}}}})
