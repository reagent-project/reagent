#!/bin/bash
set -ex
lein with-profile node-test do clean, doo node client once
test ! -f out/node_modules/react/index.js
grep "reagent.impl.template.node\$module\$react = require('react')" out/reagent/impl/template.js
