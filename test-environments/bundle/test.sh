#!/bin/bash
set -ex
clj -m cljs.main -co test-environments/bundle/build.edn --compile
# clj  -s-m cljs.main -co test-environments/bundle/build.edn -O advanced -v -c
# npx webpack --mode=development
npx karma start test-environments/bundle/karma.conf.js
