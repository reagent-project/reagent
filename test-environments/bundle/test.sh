#!/bin/bash
set -ex
clojure -m cljs.main -co test-environments/bundle/build.edn --compile
npx karma start test-environments/bundle/karma.conf.js
