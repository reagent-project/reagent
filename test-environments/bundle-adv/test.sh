#!/bin/bash
set -ex
clj -m cljs.main -co test-environments/bundle-adv/karma.edn -v --compile
npx karma start test-environments/bundle-adv/karma.conf.js
