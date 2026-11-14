#!/bin/bash
# Build the prerendered site from the dev build into target/site-preview,
# for checking static HTML + hydration locally. Needs `shadow-cljs watch client`
# (or compile client) to have run. Serve with .claude/launch.json "site-prerendered".
set -e
npx shadow-cljs compile prerender
node target/shadow-cljs-prerender/main.js target/shadow-cljs/client/public/
rm -rf target/site-preview
mkdir -p target/site-preview
cp -r target/prerender/public/* target/site-preview/
ln -s ../shadow-cljs/client/public/js target/site-preview/js
