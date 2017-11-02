#!/bin/bash

set -ex

SHA=$(git rev-parse HEAD)

cd test-environments/browser-umd-react-16

lein do clean, cljsbuild once prod

cd ../..

# Prerendering seems to work best on React 16
cd test-environments/browser-node-react-16

lein cljsbuild once prerender
node target/cljsbuild/prerender/main.js

cd ../..

lein codox

rm -fr tmp
git clone git@github.com:reagent-project/reagent-project.github.io.git tmp
rm -fr tmp/*

cp -r test-environments/browser-node-react-16/target/cljsbuild/prod/public/* tmp/
cp -r test-environments/browser-node-react-16/target/prerender/public/* tmp/
mkdir -p tmp/docs/master/
cp -r target/doc/* tmp/docs/master/

test -f tmp/index.html
test -f tmp/js/main.js
test ! -e tmp/js/out

cd tmp
git checkout -- README.md
git add .
git commit -m "Built site from $SHA"
git push
rm -rf tmp
