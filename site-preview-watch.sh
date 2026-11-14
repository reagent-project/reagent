#!/bin/bash
# Serve the prerendered site preview on port 8095 (or $1) and rebuild it
# whenever sources, CSS or docs change. Needs `shadow-cljs watch client`
# running, which also makes each rebuild fast (~2s). Uses inotifywait
# (inotify-tools) when available, otherwise polls every 2 seconds.
set -e
cd "$(dirname "$0")"

port=${1:-8095}
watch_dirs=(src demo prerender doc site/public/css target/shadow-cljs/client/public/js)

build() {
    echo "[site-preview] building..."
    if ./site-preview.sh > target/site-preview.log 2>&1; then
        echo "[site-preview] done"
    else
        echo "[site-preview] build failed:"
        tail -20 target/site-preview.log
    fi
}

mkdir -p target
build

python3 -m http.server "$port" -d target/site-preview &
server=$!
trap 'kill $server 2>/dev/null' EXIT

# The doc/*.md files are read by macros in reagentdemo.docs at compile
# time; touching that namespace makes shadow-cljs recompile it (for both
# this build and the client watch).
touch_docs_ns() {
    touch demo/reagentdemo/docs.cljs
}

if command -v inotifywait > /dev/null; then
    inotifywait -m -r -q -e close_write,create,delete,moved_to \
        --exclude '(/\.#|~$|\.swp$)' --format '%w%f' "${watch_dirs[@]}" |
        while read -r file; do
            docs_changed=
            [[ $file == doc/* ]] && docs_changed=1
            # Collect the rest of a burst of changes before building.
            while read -r -t 1 more; do
                [[ $more == doc/* ]] && docs_changed=1
            done
            if [[ -n $docs_changed ]]; then
                touch_docs_ns
                while read -r -t 1 _; do :; done
            fi
            build
        done
else
    stamp=$(mktemp)
    while sleep 2; do
        changed=$(find "${watch_dirs[@]}" -newer "$stamp" -type f 2>/dev/null)
        if [[ -n $changed ]]; then
            touch "$stamp"
            grep -q '^doc/' <<< "$changed" && touch_docs_ns && touch "$stamp"
            build
        fi
    done
fi
