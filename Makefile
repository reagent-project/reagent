PORT = 4562

PROF = dev
# PROF = prod,srcmap
# PROF = prod

CLJSBUILD = client
CLJSDIRS = src test

VERSION = 0.4.3

REACT_VERSION = 0.12.0

all: buildrun

run: openbrowser buildrun

leinbuild: setup
	lein -o with-profile $(PROF) cljsbuild once $(CLJSBUILD)

openbrowser:
	(sleep 1 && open site/test.html) &

buildrun: setup
	lein -o with-profile $(PROF) cljsbuild auto $(CLJSBUILD)

runtest:
	$(MAKE) run PROF=test,$(PROF)

runsite: setup
	(sleep 3 && open "http://127.0.0.1:$(PORT)/$$(basename $$PWD)") &
	( trap "kill 0" SIGINT SIGTERM EXIT; \
	  ( cd .. && python -m SimpleHTTPServer $(PORT) & ); \
	  lein -o with-profile $(PROF),prod cljsbuild auto $(CLJSBUILD) )

install: leinbuild
	lein install

preclean:
	rm -rf repl .repl target out
	mkdir -p vendor/reagent

clean: preclean
	rm -rf news assets
	lein -o clean

setup: preclean
	mkdir -p news assets

show-outdated:
	lein ancient :all

download-react:
	curl -L "http://fb.me/react-$(REACT_VERSION).js" \
		-o vendor/reagent/react.js
	curl -L "http://fb.me/react-$(REACT_VERSION).min.js" \
		-o vendor/reagent/react.min.js

gensite:
	node bin/gen-site.js

demobuild:
	$(MAKE) PROF=prod,demo leinbuild

buildsite: demobuild gensite

setversion:
	version=$(VERSION); \
	find . -name project.clj -o -name README.md | \
	xargs -n1 sed -i "" -e 's,\(reagent "\)\([^"]*\)",\1'"$$version"'"',g

tag: setversion
	if git rev-parse v$(VERSION) 2>/dev/null; then \
	   echo "Tag already exists"; \
	   exit 1; \
	else \
	   git commit --allow-empty -a -v -e -m"Version "$(VERSION) && \
	   git tag v$(VERSION); \
	fi
