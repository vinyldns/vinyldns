// Karma configuration
// http://karma-runner.github.io/0.10/config/configuration-file.html

const puppeteer = require('puppeteer');
process.env.CHROME_BIN = puppeteer.executablePath();

module.exports = function(config) {
    config.set({
        // base path, that will be used to resolve files and exclude
        basePath: 'public/',

        // testing framework to use (jasmine/mocha/qunit/...)
        frameworks: ['jasmine'],

        // list of files / patterns to load in the browser
        files: [
            'javascripts/moment.min.js',
            'gentelella/vendors/jquery/dist/jquery.min.js',
            'gentelella/vendors/bootstrap/dist/js/bootstrap.min.js',
            'gentelella/vendors/bootstrap-daterangepicker/daterangepicker.js',
            'javascripts/angular.min.js',
            'test_frameworks/*.js',
            'lib/services/**/*.js',
            'lib/controllers/**/*.js',
            'lib/directives/**/*.js',
            'lib/batch-change/batch-change.module.js',
            'lib/*.js',
            'lib/batch-change/*.js',
            //fixtures
            {pattern: 'mocks/*.json', watched: true, served: true, included: false}
        ],

        // list of files / patterns to exclude
        exclude: [],

        // web server port
        port: 8080,

        // level of logging
        // possible values: LOG_DISABLE || LOG_ERROR || LOG_WARN || LOG_INFO || LOG_DEBUG
        logLevel: config.LOG_INFO,

        plugins: [
            'karma-jasmine',
            'karma-chrome-launcher',
            'karma-mocha-reporter'
        ],

        // reporter types:
        // - dots
        // - progress (default)
        // - spec (karma-spec-reporter)
        // - junit
        // - growl
        // - coverage
        reporters: ['mocha'],

        // enable / disable watching file and executing tests whenever any file changes
        autoWatch: true,

        // Start these browsers, currently available:
        // - Chrome
        // - ChromeCanary
        // - Firefox
        // - Opera
        // - Safari (only Mac)
        // - PhantomJS
        // - IE (only Windows)
        browsers: ['ChromeHeadlessNoSandbox'],
        customLaunchers: {
            ChromeHeadlessNoSandbox: {
                base: 'ChromeHeadless',
                flags: ['--no-sandbox']
            }
        },

        // Continuous Integration mode
        // if true, it capture browsers, run tests and exit
        singleRun: true
    });
};
