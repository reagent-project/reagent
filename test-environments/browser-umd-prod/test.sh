#!/bin/bash
set -ex
lein with-profile prod-test do clean, doo chrome-headless client once
