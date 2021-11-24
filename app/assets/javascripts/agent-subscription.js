 $(document).ready(function() {
    //======================================================
    // GOV.UK country lookup
    // https://alphagov.github.io/accessible-autocomplete/#progressive-enhancement
    //======================================================
    // auto complete country lookup, progressive enhancement
    // need to invoke new enhanceSelectElement()
    //======================================================
    var selectEl = document.querySelector('#amlsCode');
      if(selectEl){
          accessibleAutocomplete.enhanceSelectElement({
            autoselect: true,
            defaultValue: selectEl.options[selectEl.options.selectedIndex].innerHTML,
            selectElement: selectEl
          })
      }

      function findCountry(country) {
          return country == $("#amlsCode").val();
      }

   //======================================================
      // Fix CSS styling of errors (red outline) around the country input dropdown
      //======================================================

      // Set the border colour to black with orange border when clicking into the input field
      $('.autocomplete__wrapper input').focus(function(e){
          if ($(".govuk-form-group--error .autocomplete__wrapper").length > 0) $(".autocomplete__wrapper input").css({"border" : "4px solid #0b0c0c", "-webkit-box-shadow" : "none", "box-shadow" : "none"});
      })

      // Set the border colour back to red when clicking out of the input field
      // Set the gov.uk error colour https://design-system.service.gov.uk/styles/colour/
      $('.autocomplete__wrapper input').focusout(function(e){
          if ($(".govuk-form-group--error .autocomplete__wrapper").length > 0) $(".autocomplete__wrapper input").css("border", "2px solid #d4351c");
      })


      //======================================================
      // Fix IE country lookup where clicks are not registered when clicking list items
      //======================================================

      // temporary fix for IE not registering clicks on the text of the results list for the country autocomplete
      $('body').on('mouseup', ".autocomplete__option > strong", function(e){
          e.preventDefault(); $(this).parent().trigger('click');
      })
      // temporary fix for the autocomplete holding onto the last matching country when a user then enters an invalid or blank country
      $('input[role="combobox"]').on('keydown', function(e){
          if (e.which != 13 && e.which != 9) {
              var sel = document.querySelector('.autocomplete-wrapper select');
              sel.value = "";
          }
      })


    //custom handler for AMLS auto-complete dropdown
    $('#amlsCode').change(function(){
        var changedValue = $(this).val();
        var array = [];

        $('.autocomplete__menu li').each(function(){
            array.push($(this).text())
        });

        if(array == "No results found"){
            $('#amls-auto-complete-select').append('<option id="notFound" value="NOTFOUND">No results found</option>');
            $('#amls-auto-complete-select').val('NOTFOUND').attr("selected", "selected");

        }else if(array == ""){
            $('#amls-auto-complete-select').val('').attr("selected", "selected");
        }

    });


    // by default the dropForm will be hidden so we we need this to make the form visible after loaded
    $('#dropForm').css('visibility', 'visible');


});
