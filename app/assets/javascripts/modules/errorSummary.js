(function () {

    'use strict';

    var root = this, $ = root.jQuery;

    if (typeof HMRC === 'undefined') {
        root.HMRC = {};
    }

    var ErrorSummary = function (){

        if ($('.error-summary')) {
            $('.error-summary').focus();
        }

    };

    root.HMRC.ErrorSummary = ErrorSummary;

}).call(this);
