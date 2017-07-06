// karma.conf.js
module.exports = function(config) {
  config.set({
    frameworks: ['jasmine'],
    reporters: ['spec'],
    browsers: ['PhantomJS'],
    files: [
      './node_modules/jquery/dist/jquery.min.js',
      './tests/vendor/jasmine-jquery.js',
      './javascripts/modules/*.js',
      './javascripts/src/*.js',
      './tests/spec/*.js',
      {
        pattern: './tests/fixtures/*.html',
        include: false
      }
    ]
  });
};
