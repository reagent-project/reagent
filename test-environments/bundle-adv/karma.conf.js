module.exports = function (config) {
    config.set({
        browsers: ['ChromeHeadless'],
        basePath: '../../target/bundle-adv/resources/public/',
        files: ['js/out/karma.js'],
        frameworks: ['cljs-test'],
        preprocessors: {
          'js/out/karma.js': ['webpack', 'sourcemap']
        },
        colors: true,
        logLevel: config.LOG_INFO,
        client: {
            args: ['karmaTests'],
        },
        singleRun: true,
        webpack: {
            mode: 'production'
        }
    });
};
