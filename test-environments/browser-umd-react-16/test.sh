#!/bin/bash
set -ex
lein with-profile dev,react-16 do clean, doo chrome-headless test once
test -f target/cljsbuild/test/out/cljsjs/react/development/react.inc.js
