#!/bin/bash
set -ex
clojure -m cljs.main -co test-environments/bundle-adv/karma.edn -v --compile
npx karma start test-environments/bundle-adv/karma.conf.js
npx gzip-size-cli target/bundle-adv/resources/public/js/out/karma.js
