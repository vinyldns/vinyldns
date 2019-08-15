/*global module:false*/
module.exports = function (grunt) {

    // Project configuration.
    grunt.initConfig({
        // Metadata.
        pkg: grunt.file.readJSON('package.json'),
        copy: {
            main: {
                files: [
                    // includes files within path and its sub-directories
                    { expand: true, flatten: true, src: ['node_modules/jquery/dist/jquery.min.js'], dest: 'public/javascripts' },
                    { expand: true, flatten: true, src: ['node_modules/angular/angular.min.js'], dest: 'public/javascripts' },
                    { expand: true, flatten: true, src: ['node_modules/angular-animate/angular-animate.min.js'], dest: 'public/javascripts' },
                    { expand: true, flatten: true, src: ['node_modules/angular-bootstrap/ui-bootstrap.min.js'], dest: 'public/javascripts' },
                    { expand: true, flatten: true, src: ['node_modules/bootstrap/dist/js/bootstrap.min.js'], dest: 'public/javascripts' },
                    { expand: true, flatten: true, src: ['node_modules/angular-ui-router/release/angular-ui-router.min.js'], dest: 'public/javascripts' },
                    { expand: true, flatten: true, src: ['node_modules/bootstrap/dist/css/bootstrap.min.css'], dest: 'public/stylesheets' },
                    { expand: true, flatten: true, src: ['node_modules/font-awesome/css/font-awesome.min.css'], dest: 'public/stylesheets' },
                    { expand: true, cwd: 'node_modules/gentelella', dest: 'public/gentelella', src: '**'},
                    { expand: true, flatten: true, src: ['public/custom/**/*.js', '!public/custom/**/*.spec.js'], dest: 'public/javascripts' },
                    { expand: true, flatten: true, src: ['public/custom/**/*.css'], dest: 'public/stylesheets' }
                ]
            },
            unit: {
                files: [
                    { expand: true, flatten: true, src: ['node_modules/angular-mocks/angular-mocks.js'], dest: 'public/test_frameworks' },
                    { expand: true, flatten: true, src: ['node_modules/jasmine-jquery/lib/jasmine-jquery.js'], dest: 'public/test_frameworks' },
                ]
            }
        },
        injector: {
            local_dependencies: {
                files: {
                    'app/views/main.scala.html': [
                        'public/gentelella/vendors/jquery/dist/jquery.min.js',
                        'public/gentelella/vendors/bootstrap/dist/js/bootstrap.min.js',
                        'public/bower_components/moment/moment.js',
                        'public/gentelella/vendors/bootstrap-daterangepicker/daterangepicker.js',
                        'public/javascripts/ui-bootstrap.min.js',
                        'public/javascripts/angular.min.js',
                        'public/lib/**/*.module.js',
                        'public/lib/**/*.js',
                        'public/app.js',
                        'public/gentelella/build/js/custom.js',
                        'public/js/custom.js',
                        '!public/lib/**/*.spec.js'
                    ]
                }
            }
        },
        karma: {
            unit: {
                configFile: 'karma.conf.js'
            }
        },
        clean: {
            js: ['public/javascripts/*'],
            css: ['public/stylesheets/*'],
            unit: ['public/test_frameworks/*'],
            gentelella: ['public/gentelella/*']
        },
        ngtemplates: {
            'views.vinyl': {
                src: 'public/custom/**/*.html',
                dest: 'public/custom/views.vinyl.js',
                options: {
                    standalone: true,
                    quotes: 'single',
                    url: function (url) { return url.replace('public/custom/', ''); }
                }
            }
        }
    });

    grunt.loadNpmTasks('grunt-contrib-copy');
    grunt.loadNpmTasks('grunt-injector');
    grunt.loadNpmTasks('grunt-contrib-clean');
    grunt.loadNpmTasks('grunt-angular-templates');
    grunt.loadNpmTasks('grunt-karma');
    grunt.loadNpmTasks('grunt-mocha-phantomjs');

    // Default task
    grunt.registerTask('default', ['clean', 'ngtemplates', 'copy:main', 'injector']);
    // Unit tests
    grunt.registerTask('unit', ['default', 'ngtemplates', 'copy:unit', 'karma:unit', 'clean:unit']);
};
