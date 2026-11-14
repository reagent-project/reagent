#!/bin/bash
# Build the prerendered site from the dev build into target/site-preview,
# for checking static HTML + hydration locally. Needs `shadow-cljs watch client`
# (or compile client) to have run. site-preview-watch.sh (launch.json
# "site-prerendered") serves it and reruns this on changes.
set -e
# reagentdemo.docs reads doc/*.md in macros, which the shadow-cljs cache
# doesn't track; drop its cached output so the guides are always current.
rm -f .shadow-cljs/builds/prerender/dev/ana/reagentdemo/docs.cljs.cache.transit.json
npx shadow-cljs compile prerender
node target/shadow-cljs-prerender/main.js target/shadow-cljs/client/public/
rm -rf target/site-preview
mkdir -p target/site-preview
cp -r target/prerender/public/* target/site-preview/
ln -s ../shadow-cljs/client/public/js target/site-preview/js
