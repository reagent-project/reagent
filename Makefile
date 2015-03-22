

PROF = 
PORT = 3449

SITEDIR = outsite/public
OUTPUTDIR = $(SITEDIR)/js/out


# convenience shortcuts for continous building
##############################################

# development build with auto-reloading
run: figwheel

# development build with auto-reloading and site generation
runsite:
	@$(MAKE) run PROF=+site,$(PROF)

# development build with figwheel, but no tests
runnotest:
	@$(MAKE) run PROF=+dev-notest,$(PROF)

# production build with auto-rebuild
runprod: clean
	@$(MAKE) serve-site PROF=prod,$(PROF)

# production build with auto-rebuild and testing
runprodtest: clean
	@$(MAKE) serve-site PROF=prod-test,$(PROF)

clean:
	lein clean


## Subtargets

figwheel: trigger-build
	@echo "Will start figwheel server at: http://127.0.0.1:$(PORT)\n"
	lein with-profile $(PROF), figwheel

serve-site: trigger-build
	@echo "Starting site at: http://127.0.0.1:$(PORT)/public\n"
	( trap "kill 0" SIGINT SIGTERM EXIT; \
	  ( cd $(SITEDIR)/.. && python -m SimpleHTTPServer $(PORT) & ); \
	  lein with-profile $(PROF), cljsbuild auto )

trigger-build:
        # always trigger build to make sure page-generation works
	@echo "(ns empty.generated.ns)" > demo/empty.cljs
	@(echo "/* Generated, do not modify */\n\n" && \
            cat examples/todomvc/todos.css examples/simple/example.css)  \
            > site/public/css/examples.css



## gh-pages support
###################

# build site and push upstream to the gh-pages branch
push-gh-pages: build-gh-pages
	git push origin gh-pages:gh-pages

# build site and push to reagent-project's doc site
push-project-docs: build-gh-pages
	git push --force \
          https://github.com/reagent-project/reagent-project.github.io.git \
          gh-pages:master

# build site into a gh-pages branch
build-gh-pages: gen-site gh-pages-add

gen-site: clean
	lein with-profile prod cljsbuild once

# copy contents of $(SITEDIR) to branch gh-pages
gh-pages-add:
        # sanity check
	test -f $(SITEDIR)/index.html
	test ! -e $(OUTPUTDIR)
        # make sure gh-pages branch exists
	git show-branch gh-pages || true | git mktree | \
          xargs git commit-tree | xargs git branch gh-pages
        # clone gh-pages branch, and commit site to that
	cd $(SITEDIR) && \
	rm -rf .git tmp && \
	git clone ../.. -lnb gh-pages tmp && \
	mv tmp/.git . && \
	git add . && git commit -m "Updated" && \
	git push && rm -rf .git tmp


## Misc utilities
#################

show-outdated:
	lein ancient :all

VERSION := `sed -n -e '/(defproject reagent/ s/.*"\(.*\)"/\1/p' project.clj`

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
