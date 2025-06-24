#!/bin/bash
set -ex
clojure -M:dev -m cljs.main -co test-environments/bundle-adv/karma.edn -v --compile
npx karma start test-environments/bundle-adv/karma.conf.js

gzip -fk target/bundle-adv/resources/public/js/out/karma.js
ls -lh target/bundle-adv/resources/public/js/out/
