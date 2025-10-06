#!/bin/bash
set -ex
clojure -M:dev -m cljs.main -co test-environments/bundle-adv/build.edn -v --compile
npx webpack --config=test-environments/bundle-adv/webpack.config.js
cp -r site/public/index.html site/public/css target/bundle-adv/resources/public/
