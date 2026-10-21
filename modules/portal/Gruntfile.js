module.exports = function(grunt) {

  const license = `/*
* Copyright 2018 Comcast Cable Communications Management, LLC
*
* Licensed under the Apache License, Version 2.0 (the \\"License\\");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an \\"AS IS\\" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/`;

  grunt.loadNpmTasks('grunt-contrib-copy');
  grunt.loadNpmTasks('grunt-contrib-clean');
  grunt.loadNpmTasks('grunt-karma');
  grunt.loadNpmTasks('grunt-mocha-phantomjs');
  grunt.loadNpmTasks('grunt-contrib-concat');

  // Project configuration.
  grunt.initConfig({
    pkg: grunt.file.readJSON('package.json'),

    copy: {
      // Collect all of the javascript and CSS files that we need (we'll combine them later)
      main: {
        files: [
          {expand: true, flatten: true, src: ['node_modules/angular/angular.min.js'], dest: 'public/js'},
          {expand: true, flatten: true, src: ['node_modules/bootstrap/dist/js/bootstrap.min.js'], dest: 'public/js'},
          {expand: true, flatten: true, src: ['node_modules/jquery/dist/jquery.min.js'], dest: 'public/js'},
          {expand: true, flatten: true, src: ['node_modules/moment/min/moment.min.js'], dest: 'public/js'},
          {expand: true, flatten: true, src: ['node_modules/jquery-ui-dist/jquery-ui.js'], dest: 'public/js'},
          {expand: true, flatten: true, src: ['node_modules/angular-cron-jobs/dist/angular-cron-jobs.min.js'], dest: 'public/js'},
          {expand: true, flatten: true, src: ['node_modules/select2/dist/js/select2.min.js'], dest: 'public/js'},

          {expand: true, flatten: true, src: ['node_modules/bootstrap/dist/css/bootstrap.min.css'], dest: 'public/css'},
          {expand: true, flatten: true, src: ['node_modules/font-awesome/css/font-awesome.min.css'], dest: 'public/css'},
          {expand: true, flatten: true, src: ['node_modules/jquery-ui-dist/jquery-ui.css'], dest: 'public/css'},
          {expand: true, flatten: true, src: ['node_modules/angular-cron-jobs/dist/angular-cron-jobs.min.css'], dest: 'public/css'},
          {expand: true, flatten: true, src: ['node_modules/select2/dist/css/select2.min.css'], dest: 'public/css'},

          // We're picking just the resources we need from the gentelella UI framework and temporarily storing them in mapped/ui/
          {expand: true, flatten: true, cwd: 'node_modules/gentelella', dest: 'mapped/ui', src: '**/jquery.{smartWizard,dataTables.min,mousewheel.min}.js'},
          {expand: true, flatten: true, cwd: 'node_modules/gentelella', dest: 'mapped/ui', src: '**/bootstrap-daterangepicker/daterangepicker.js'},
          {expand: true, flatten: true, cwd: 'node_modules/gentelella', dest: 'mapped/ui', src: '**/build/css/custom.min.css'},
          {expand: true, flatten: true, cwd: 'node_modules/gentelella', dest: 'mapped/ui', src: '**/{daterangepicker,animate.min}.css'},
          {expand: true, flatten: true, cwd: 'node_modules/gentelella', dest: 'public/fonts', src: '**/fonts/*.{woff,woff2,ttf}'},

          {expand: true, flatten: true, src: ['public/custom/**/*.js', '!public/custom/**/*.spec.js'], dest: 'public/js'},
          {expand: true, flatten: true, src: ['public/custom/**/*.css'], dest: 'public/css'},
        ],
      },
      // Unit test files
      unit: {
        files: [
          {expand: true, flatten: true, src: ['node_modules/angular-mocks/angular-mocks.js'], dest: 'public/test_frameworks'},
          {expand: true, flatten: true, src: ['node_modules/jasmine-jquery/lib/jasmine-jquery.js'], dest: 'public/test_frameworks'},
        ],
      },
    },

    // Unit test configuration
    karma: {
      unit: {
        configFile: 'karma.conf.js',
      },
    },

    // Combine multiple files to make it easier to import and use them
    concat: {
      options: {
        sourceMap: true,
      },
      css: {
        files: {
          'public/css/ui.css': ['mapped/ui/*.css'],
        },
      },
      vinyldns: {
        options: {
          banner: license,
          separator: ';\n',
          process: (src, filepath) => {
            return '/* Source: ' + filepath + '*/\n' +
                src.replace(/(^|\n)[ \t]*('use strict'|"use strict");?\s*/g, '$1').replace(/\/\*.+?\*\//gs, '').replace(/\n{2,}/g, '\n');
          },
        },
        files: {
          'public/js/vinyldns.js': [
            'public/lib/**/*.module.js',
            'public/lib/**/*.js',
            '!public/lib/**/*.spec.js',
          ],
        },
      },
      ui_javascript: {
        options: {
          separator: ';\n',
          process: (src, filepath) => {
            return '/* Source: ' + filepath + ' */\n' +
                src.replace(/(^|\n)[ \t]*('use strict'|"use strict");?\s*/g, '$1');
          },
        },
        files: {
          'public/js/ui.js': ['mapped/ui/**/*.js'],
        },
      },
      ui_css: {
        options: {
          separator: '\n',
          process: (src, filepath) => {
            return '/* Source: ' + filepath + ' */\n' + src;
          },
        },
        files: {
          'public/css/ui.css': ['mapped/ui/**/*.css'],
        },
      },
    },

    clean: {
      js: ['public/js/*', '!public/js/custom.js'],
      css: ['public/css/*', '!public/css/{theme-overrides,vinyldns}.css'],
      mapped: ['mapped/'],
      fonts: ['public/fonts'],
      unit: ['public/test_frameworks/*'],
    },
  });

  // Default task
  grunt.registerTask('default', ['clean', 'copy:main', 'concat', 'clean:mapped']);
  // Unit tests
  grunt.registerTask('unit', ['default', 'copy:unit', 'karma:unit', 'clean:unit']);
};
