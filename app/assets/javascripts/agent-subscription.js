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
