
PROF = dev
# PROF = dev,test,srcmap
# PROF = prod,test
# PROF = prod

CLJSBUILD = client

all: autocompile

run: openbrowser autocompile

openbrowser:
	(sleep 1 && open example.html) &

autocompile:
	rm -rf target
	lein with-profile $(PROF) cljsbuild auto $(CLJSBUILD)

clean:
	lein -o clean
