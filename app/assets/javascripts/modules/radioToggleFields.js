/**
* Radio Toggle Fields Module
*
* Description:      Radio button toggles target elements visibility - useful for showing and hiding elements
* Features:         Multiple targets - Separate with a comma for multiple target ids eg: data-toggle-id="target1,target2,target3"
* Basic usage:

* HTML

    <fieldset>
        <legend>Radio Question</legend>
        <div class="multiple-choice">
            <input id="radio_true" type="radio" name="radio_question" value="true">
            <label for="radio_true">Yes</label>
        </div>
        <div class="multiple-choice">
            <input id="radio_false" type="radio" name="radio_question" value="false" data-toggle-id="target">
            <label for="radio_false">No</label>
        </div>
    </fieldset>

    <div id="target" data-toggle-hidden>
        ...
    </div>

* CSS

    [data-toggle-hidden]{
        display: none;
    }

    .no-js{
        [data-toggle-hidden] {
            display: block !important;
        }
    }
*
*
*/

(function () {

    'use strict';

    var root = this, $ = root.jQuery;

    if (typeof HMRC === 'undefined') {
        root.HMRC = {};
    }

    var RadioToggleFields = function (){

        var getDataToggleIds = function (scope) {
            var ids = [];
            $(scope).closest('fieldset').find('[data-toggle-id]').each(function(){
                ids.push($(this).attr('data-toggle-id').split(','));
            });
            return [].concat.apply([], ids);
        };

        var uniqueArray = function (array) {
            var unique = [];
            $.each(array, function(i, el){
                if($.inArray(el, unique) === -1){
                    unique.push(el);
                }
            });
            return unique;
        };

        var removeFromArray = function(array, value){
            for(var i = array.length; i--;) {
                if(array[i] === value) {
                    array.splice(i, 1);
                }
            }
            return array;
        };

        //Aria attributes and hide if no css

        $('[data-toggle-hidden]').each(function(){
            $(this).css('display', 'none').attr({'aria-hidden':'true'});
        });

        //Toggle on change

        $('input:radio').change(function(){
            var _this = this;

            //Get ids

            var ids = uniqueArray(getDataToggleIds(_this));

            //When data-toggle-id checked

            if($(_this).is('[data-toggle-id]:checked')){

                //Show correct ids

                $.each($(_this).attr('data-toggle-id').split(','), function( index, value ) {
                    $('#'+value).css('display', 'block').attr({'aria-hidden':'false'});
                    ids = removeFromArray(ids,value);
                });

                //Hide the rest

                $.each(ids, function( index, value ) {
                    $('#'+value).css('display', 'none').attr({'aria-hidden':'true'});
                });
            }else{

                //Hide the rest

                $.each($(this).closest('fieldset').find('[data-toggle-id]'), function( index, value ) {
                    $.each($(this).attr('data-toggle-id').split(','), function( index, value ) {
                        $('#'+value).css('display', 'none').attr({'aria-hidden':'true'});
                    });
                });
            }
        });

        //Onload show each checked

        $('input:radio[data-toggle-id]').each(function(){
            if($(this).is(':checked')){
                var ids = $(this).attr('data-toggle-id').split(',');
                $.each(ids, function( index, value ) {
                    $('#'+value).css('display', 'block').attr({'aria-hidden':'false'});
                });
            }
        });
    };

    root.HMRC.RadioToggleFields = RadioToggleFields;

}).call(this);
