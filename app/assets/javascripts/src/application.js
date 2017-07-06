(function (HMRC, $) {
    'use strict';

    $(document).ready(function() {
        new HMRC.RadioToggleFields();
        new HMRC.ErrorSummary();
    });

}(window.HMRC = window.HMRC|| {}, jQuery));
