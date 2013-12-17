PORT = 4562

PROF = dev
PROF = dev,test
# PROF = dev,test,srcmap
# PROF = prod,test
# PROF = prod

CLJSBUILD = client
CLJSDIRS = src test

VERSION = 0.0.2-SNAPSHOT

all: buildrun

run: openbrowser buildrun

leinbuild: setup
	lein -o cljsbuild once $(CLJSBUILD)

openbrowser:
	(sleep 1 && open -a "Google Chrome" site/test.html) &

buildrun: setup
	lein -o with-profile $(PROF) cljsbuild auto $(CLJSBUILD)

install: setup
	lein install

preclean:
	rm -rf repl .repl target

clean: preclean
	lein -o clean

setup: preclean

gen-react: bower_components
	node bin/gencljs.js

show-outdated:
	lein ancient :all

veryclean: clean
	rm -rf bower_components

bower_components:
	bower install react#v0.5.1

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
