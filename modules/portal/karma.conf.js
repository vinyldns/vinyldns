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
      'js/jquery.min.js',
      'js/jquery-ui-dist.js',
      'js/jquery-ui.js',
      'js/bootstrap.min.js',
      'js/angular.min.js',
      'js/moment.min.js',
      'js/ui.js',
      'js/angular-cron-jobs.min.js',
      'test_frameworks/*.js',
      'js/vinyldns.js',
      'lib/services/**/*.spec.js',
      'lib/controllers/**/*.spec.js',
      'lib/directives/**/*.spec.js',
      'lib/*.js',
      //fixtures
      {pattern: 'mocks/*.json', watched: true, served: true, included: false},
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
      'karma-mocha-reporter',
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
        flags: ['--no-sandbox'],
      },
    },

    // Continuous Integration mode
    // if true, it capture browsers, run tests and exit
    singleRun: true,
  });
};
