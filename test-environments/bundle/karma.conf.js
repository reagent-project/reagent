module.exports = function (config) {
    config.set({
        browsers: ['ChromeHeadless'],
        basePath: '../../target/bundle/resources/public/',
        files: [
          // Karma will try running test adapter before all
          // files loaded by Cljs are ready.
          '../../../../test-environments/bundle/workaround.js',
          'js/out/index.js',
          {pattern: 'js/out/**/*.js', included: false},
          // Source maps
          {pattern: 'js/out/**/*.cljs', included: false},
          {pattern: 'js/out/**/*.js.map', included: false}
        ],
        frameworks: ['cljs-test'],
        preprocessors: {
          'js/out/index.js': ['webpack', 'sourcemap']
        },
        // Cljs asset-path
        proxies: {
          '/js/out/': '/base/js/out/'
        },
        colors: true,
        logLevel: config.LOG_INFO,
        client: {
            args: ['reagenttest.runtests.karma_tests'],
        },
        singleRun: true,
        webpack: {
            mode: 'development'
        }
    });
};
