#!/bin/bash

set -ex

SHA=$(git rev-parse HEAD)

# sanity check
rm -fr tmp
git clone git@github.com:reagent-project/reagent-project.github.io.git tmp
rm -fr tmp/*

# Prerendering seems to work best on React 16
cd test-environments/browser-node-react-16

lein do clean, cljsbuild once prod

cp -r target/cljsbuild/prod/public/* ../../tmp/

lein cljsbuild once prerender
node target/cljsbuild/prerender/main.js
cp -r target/prerender/public/* ../../tmp/

cd ../..

test -f tmp/index.html
test -f tmp/js/main.js
test ! -e tmp/js/out

lein codox

mkdir -p tmp/docs/master/
cp -r target/doc/* tmp/docs/master/

cd tmp
git checkout -- README.md
git add .
git commit -m "Built site from $SHA"
# git push
# rm -rf tmp
