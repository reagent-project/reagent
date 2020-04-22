# Reagent development

## Running tests

The tests use [Karma](https://karma-runner.github.io/2.0/index.html) to run tests on browsers. You need to install `karma` command to run the tests:

```bash
npm install -g karma-cli
```

To prepare different environments for tests run:

```bash
./prepare-tests.sh
```

After this, you can run the full test set:

```bash
./run-tests.sh
```

Running all the tests can take a while, so while developing Reagent,
you might want to focus on one test environment, and use Figwheel to
run tests on your browser:

```
lein figwheel client # For Cljsjs
lein figwheel client-npm # NPM

# Open http://0.0.0.0:3449 on a browser
# Check console for test output
```

## Building package

To build Reagent and use built version in your applications run `lein install`
and update the dependency on your app to use the version that was installed.

Note that if `project.clj` uses a version that is released on Clojars, this command
will overwrite that version on your local Maven repository. To restore
real version, remove directory corresponding to the version from `~/.m2/repository/reagent/reagent/`.
