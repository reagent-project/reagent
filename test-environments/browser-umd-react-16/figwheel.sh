#!/bin/bash
set -x
lein with-profile dev,react-16 figwheel
