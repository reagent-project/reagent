#!/bin/bash

set -e

SHA=$(git rev-parse HEAD)

# lein 'do' clean, cljsbuild once prod-npm, cljsbuild once prerender
npx shadow-cljs release client prerender

# node target/cljsbuild/prerender/main.js target/cljsbuild/prod-npm/public/
node target/shadow-cljs-prerender/main.js target/shadow-cljs-release/client/public/

lein codox

rm -fr tmp
if [[ -n $GITHUB_ACTOR ]]; then
    git config --global user.email "14146879+github-actions[bot]@users.noreply.github.com"
    git config --global user.name "github-actions[bot]"
    git clone "https://${GITHUB_ACTOR}:${SITE_TOKEN}@github.com/reagent-project/reagent-project.github.io.git" tmp
else
    git clone git@github.com:reagent-project/reagent-project.github.io.git tmp
fi

# Remove everything to ensure old files are removed
rm -fr tmp/*

cp -r target/cljsbuild/prod-npm/public/* tmp/
cp -r target/prerender/public/* tmp/
mkdir -p tmp/docs/master/
cp -r target/doc/* tmp/docs/master/

test -f tmp/index.html
test -f tmp/js/main.js
test ! -e tmp/js/out

cd tmp

# Restore files not created by this script
git add docs/master/
git checkout -- README.md docs/
git add .
git commit -m "Built site from $SHA"
git push
rm -rf tmp
