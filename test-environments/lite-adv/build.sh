#!/bin/bash
set -ex
rm -rf target/lite-adv
clojure -M:dev:lite-cljs -m cljs.main -co test-environments/lite-adv/build.edn -v --compile
if [[ -f target/lite-adv/resources/public/js/out/index.js ]]; then
  npx webpack --config=test-environments/lite-adv/webpack.config.js
fi
cp -r site/public/index.html site/public/css target/lite-adv/resources/public/
