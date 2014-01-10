

(defproject cloact "0.1.0-SNAPSHOT"
  :url "http://github.com/holmsand/cloact"
  :license {:name "MIT"}
  :description "A simple ClojureScript interface to React"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2138"]]
  :plugins [[lein-cljsbuild "1.0.1"]]
  :profiles {:prod {:cljsbuild
                    {:builds
                     {:client {:compiler
                               {:optimizations :advanced
                                :preamble ^:replace ["cloact/react.min.js"]
                                :pretty-print false}}}}}
             :test {:plugins [[com.cemerick/clojurescript.test "0.2.1"]]
                    :cljsbuild
                    {:builds
                     {:client {:source-paths ["test"
                                              "examples/todomvc/src"
                                              "examples/simple/src"]}}}}
             :srcmap {:cljsbuild
                      {:builds
                       {:client
                        {:compiler
                         {:source-map "target/cljs-client.js.map"
                          :source-map-path "client"}}}}}}
  :source-paths ["src"]
  :cljsbuild
  {:builds
   {:client {:source-paths ["src"]
             :compiler
             {:preamble ["cloact/react.js"]
              :output-dir "target/client"
              :output-to "target/cljs-client.js"
              :pretty-print true}}}})
