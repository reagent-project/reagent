/* jshint strict: false */
/* globals configData */

/*
 * Doo reads this file from classpath runners/karma.conf.js
 * This sets up junit reporter.
 */

var path = require('path');

module.exports = function(config) {

  var suite = path.basename(process.cwd());

  configData.plugins = ['karma-*'];

  configData.logLevel = config.LOG_WARN;

  configData.reporters = ['dots', 'junit'];
  configData.junitReporter = {
    outputDir: (process.env.CIRCLE_TEST_REPORTS || 'junit'),
    outputFile: suite + '.xml',
    suite: suite, // suite will become the package name attribute in xml testsuite element
    useBrowserName: false // add browser name to report and classes names
  };

  config.set(configData);
};
