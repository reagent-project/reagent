# Reagent development

## Running tests

To prepare different environments for tests run:

```bash
./prepare-test.sh
```

After this, you can run the full test set:

```bash
./run-tests.sh
```

Running all the tests can take a while, so while developing Reagent,
you might want to focus on one test environment, and use Figwheel to
run tests on your browser:

```
cd test-environments/browser-umd-react-16
# If build requires e.g. Lein profiles or such,
# the folder contains figwheel.sh script:
./figwheel.sh
# Else, just run figwheel normally:
lein figwheel

# Open http://0.0.0.0:3449 on a browser
# Check console for test output
```

## Building package

To build Reagent and use built version in your applications run `lein install`
and update the dependency on your app to use the version that was installed.

Note that if `project.clj` uses a version that is released on Clojars, this command
will overwrite that version on your local Maven repository. To restore
real version, remove directory corresponding to the version from `~/.m2/repository/reagent/reagent/`.
