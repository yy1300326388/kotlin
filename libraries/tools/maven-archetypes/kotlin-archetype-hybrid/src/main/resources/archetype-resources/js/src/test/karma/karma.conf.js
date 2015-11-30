module.exports = function (config) {
    config.set({
            frameworks: ['qunit'],
            reporters: ['progress', 'junit'],
            files: [
                '../../../target/jsjs/kotlin.js',
                '../../../target/jsjs/*.js',
                '../../../../shared/target/classes/*.js',
                '../../../target/classes/*.js'
            ],
            exclude: [],
            port: 9876,
            runnerPort: 9100,
            colors: true,
            autoWatch: false,
            browsers: [
                'PhantomJS'
            ],
            captureTimeout: 5000,
            //singleRun: false,
            singleRun: true,
            reportSlowerThan: 500,

            junitReporter: {
                outputFile: '../../../target/reports/test-results.xml',
                suite: ''
            },
            preprocessors: {
                '**/*.js': ['sourcemap']
            }
        }
    )
};