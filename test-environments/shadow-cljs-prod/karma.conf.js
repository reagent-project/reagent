module.exports = function (config) {
    config.set({
        browsers: ['ChromeHeadless'],
        basePath: '../../target/shadow-cljs/resources/public/js/',
        files: ['karma.js'],
        frameworks: ['cljs-test'],
        plugins: ['karma-cljs-test', 'karma-chrome-launcher'],
        colors: true,
        logLevel: config.LOG_INFO,
        client: {
            args: ['shadow.test.karma.init'],
            singleRun: true
        }
    });
};
