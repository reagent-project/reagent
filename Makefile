PORT = 4562

PROF = dev
PROF = dev,test
# PROF = dev,test,srcmap
# PROF = prod,test
# PROF = prod

CLJSBUILD = client
CLJSDIRS = src test

VERSION = 0.0.4-SNAPSHOT

all: buildrun

run: openbrowser buildrun

leinbuild: setup
	lein -o cljsbuild once $(CLJSBUILD)

openbrowser:
	(sleep 1 && open site/test.html) &

buildrun: setup
	lein -o with-profile $(PROF) cljsbuild auto $(CLJSBUILD)

install: leinbuild
	lein install

preclean:
	rm -rf repl .repl target out

clean: preclean
	lein -o clean

setup: preclean copyjs

show-outdated:
	lein ancient :all

veryclean: clean
	rm -rf bower_components

bower_components:
	bower install react#v0.8.0

src/cloact/impl/react.min.js: bower_components/react/react-with-addons.min.js Makefile
	cp $< $@

copyjs: bower_components src/cloact/impl/react.min.js

setversion:
	version=$(VERSION); \
	find . -name project.clj | \
	xargs -n1 sed -i "" -e 's,\(cloact "\)\([^"]*\)",\1'"$$version"'"',g

tag: setversion
	if git rev-parse v$(VERSION) 2>/dev/null; then \
	   echo "Tag already exists"; \
	   exit 1; \
	else \
	   git commit --allow-empty -a -m"Version "$(VERSION); \
	   git tag v$(VERSION); \
	fi
