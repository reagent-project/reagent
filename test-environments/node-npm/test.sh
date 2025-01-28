#!/bin/bash
set -ex
rm -rf target/cljsbuild/node-test-npm/
lein doo node node-test-npm once
test ! -f target/cljsbuild/node-test-npm/out/node_modules/react/index.js
grep "reagent.impl.template.node\$module\$react = require('react')" target/cljsbuild/node-test-npm/out/reagent/impl/template.js
