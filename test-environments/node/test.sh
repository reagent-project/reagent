#!/bin/bash
set -ex
lein do clean, doo node node-test once
test ! -f target/cljsbuild/node-test/out/node_modules/react/index.js
grep "reagent.impl.template.node\$module\$react = require('react')" target/cljsbuild/node-test/out/reagent/impl/template.js
