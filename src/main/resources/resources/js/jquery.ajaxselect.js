(function($) {

    var methods = {
        init: function(options) {
            return this.each(function() {
                var element = $(this);
                var chosenXhr = null;
                var timer = null;
                var selectoptions = [];
                var ajaxcalls = [];

                // check for settings
                var readOnly = false;
                if (options && options.readOnly) {
                    element.attr("readonly", "true");
                    readOnly = true;
                }
                
                var width = "40%";
                if (options && options.width !== undefined &&  options.width !== "") {
                    width = options.width;
                }
                if ($(window).width() <= 767) {
                    width = "100%";
                }

                if ($("body").hasClass("rtl")) {
                    $(element).addClass("chosen-rtl");
                }
                
                var getPlaceholder = function() {
                    var placeholder = "";
                    if ($(element).find("option[value=\"\"]").length > 0) {
                        placeholder = $(element).find("option[value=\"\"]").text();
                        $(element).find("option[value=\"\"]").text("");
                    }
                    if (placeholder === "") {
                        placeholder = " ";
                    }
                    return placeholder;
                };
                
                $(element).find("options").each(function(){
                    selectoptions.push($(this).val());
                });
                
                $(element).chosen({width: width, placeholder_text : getPlaceholder()});
                
                if (!readOnly) {
                    methods.setChosenObject($(element));
                    
                    var chosenContainer = $(element).next('.chosen-container');
                    
                    var getValues = function (name) {
                        //get enabled input field oni
                        var el = $('[name=' + name + ']').filter("input[type=hidden]:not([disabled=true]), :enabled, [disabled=false]");
                        var values = new Array();

                        if ($(el).is("select")) {
                            el = $(el).find("option:selected");
                        } else if ($(el).is("input[type=checkbox], input[type=radio]")) {
                            el = $(el).filter(":checked");
                        } 

                        $(el).each(function() {
                            values.push($(this).val());
                        });

                        return values;
                    };
                    
                    //render the options and update the autocomplate list
                    var renderOptions = function(field, keyword, data, values) {
                        //snapshot before chosen:updated resets the search input, so we know what the user actually had typed
                        var typedValBeforeRender = $.trim($(field).val());
                        var defaultValues = [];
                        var hasValue = false;
                        var hasChanges = false;
                        $(element).empty();
                        $(element).append('<option value=""></option>');
                        for (var i=0, len=data.length; i < len; i++) {
                            selectoptions = [];
                            if ($.inArray(data[i].value, selectoptions) === -1) {
                                var selected = "";
                                if ($.inArray(data[i].value, values) !== -1) {
                                    selected = "selected=\"selected\"";
                                    hasValue = true;
                                }
                                if (data[i].selected !== undefined && data[i].selected.toLowerCase() === "true") {
                                    defaultValues.push(data[i].value);
                                }
                                var option = $("<option "+selected+" >"+UI.escapeHTML(data[i].label)+"</option>");
                                option.attr("value", data[i].value);
                                $(element).append(option);
                                selectoptions.push(data[i].value);
                                hasChanges = true;
                            }
                        }
                        if (hasChanges && defaultValues.length > 0 && !hasValue) {
                            for (var dv in defaultValues) {
                                $(element).find("option[value='"+defaultValues[dv]+"']").attr("selected", "selected");
                            }
                        } else if (!hasValue) {
                            $(element).find("option[value='']").attr("selected", "selected");
                        }
                        if (hasChanges && !$(element).is(".section-visibility-disabled")) {
                            $('[name='+options.paramName+']:not(form):not(.section-visibility-disabled)').not(element).trigger("change");
                        }

                        //let chosen re-parse the updated option list before searching/highlighting it
                        $(element).trigger("chosen:updated");

                        //restore whatever the user actually had typed - chosen:updated above blanks the search input as a side effect,
                        //and a delayed/out-of-order response's own keyword may be stale, so just put back the real pre-render snapshot
                        $(field).val(typedValBeforeRender);

                        //hightlight the options with keyword - but only if the dropdown is actually meant to be open,
                        //since chosen's own results_search() force-opens it otherwise (e.g. right after a selection,
                        //when chosen deliberately closes the dropdown but still refocuses the search field)
                        var chosenInstance = $(element).data("chosen");
                        if (chosenInstance.results_showing) {
                            chosenInstance.results_search();
                        }
                    };
                        
                    var updateOptions = function(keyword, timeout, field) {
                        //get current selected values
                        var values = getValues(options.paramName);
                        var valueStr = "";
                        if (values !== null && values !== undefined) {
                            if (Array.isArray(values)) {
                                valueStr = values.join(";");
                            } else {
                                valueStr = values;
                            }
                        }
                        
                        //get dependency field values to construct URL
                        var params = "";
                        if (options.requestParams !== undefined) {
                            params = FormUtil.getFieldsAsUrlQueryString(options.requestParams, $(field).closest("div.subform-container"));

                            if (params !== "") {
                                params = "?" + params;
                            }
                        }

                        if (ajaxcalls[params + "|" + valueStr + "|" + keyword] !== undefined){ //already fetched this exact query before, render immediately without waiting for the debounce delay
                            renderOptions(field, keyword, ajaxcalls[params + "|" + valueStr + "|" + keyword], values);
                            return;
                        }

                        timer = setTimeout(function() {
                            if (chosenXhr) {
                                chosenXhr.abort();
                            }
                            
                            chosenXhr = $.ajax({
                                dataType: "json",
                                url: options.contextPath + "/web/json/app/"+options.appId+"/"+options.appVersion+"/plugin/org.joget.marketplace.AjaxSelectbox/service" + params,
                                method: "POST",
                                data: {
                                    _paramName: options.paramName,
                                    _n: options.nonce,
                                    _listId: options.listId,
                                    _idField: options.idField,
                                    _displayField: options.displayField,
                                    _allowEmpty: options.allowEmpty,
                                    _defaultOptions: options.defaultOptions,
                                    _values : valueStr,
                                    _keyword : keyword
                                },
                                success: function(data){
                                    ajaxcalls[params + "|" + valueStr + "|" + keyword] = data; //store the options of a keyword for later used
                                    renderOptions(field, keyword, data, values);
                                    chosenXhr = null;
                                }
                            });
                        }, timeout);
                    };
                    
                    var complete = true;
                    $(chosenContainer).find(".search-field > input, .chosen-search > input").on('compositionstart', function () {
                        complete = false;
                    });

                    //process the field's current (now finalized) value directly here rather than waiting for a
                    //subsequent keyup/input event to notice complete flipped back to true - the order in which
                    //compositionend and its accompanying input event fire is inconsistent across browsers/IMEs
                    //(e.g. Windows Japanese IME vs Android Gboard), so waiting for that next event was leaving
                    //the search stuck until the user pressed Enter/Space to generate an unrelated new event.
                    $(chosenContainer).find(".search-field > input, .chosen-search > input").on('compositionend', function () {
                        complete = true;
                        handleSearchInput.call(this);
                    });

                    //reload the full option list every time the dropdown is genuinely opened, regardless of whether
                    //a value is already selected. chosen:showing_dropdown is chosen's own event for this and only
                    //fires when it actually shows the dropdown - unlike a plain focus handler, it isn't confused by
                    //chosen refocusing the search field as part of its close/cleanup flow right after a selection.
                    $(element).on("chosen:showing_dropdown", function () {
                        updateOptions("", options.afterTypeDelay, $(chosenContainer).find('.chosen-search > input, .search-field > input'));
                    });

                    var handleSearchInput = function () {
                        if (complete) {
                            var msg, untrimmed_val, val;
                            untrimmed_val = $(this).val();
                            val = $.trim($(this).val());

                            msg = val.length < options.minTermLength ? options.keepTypingMsg : options.lookingForMsg;
                            $(chosenContainer).find('.no-results').text(msg);

                            if (val === $(this).data('prevVal')) {
                                return false;
                            }
                            $(this).data('prevVal', val);

                            if (timer !== null && timer !== undefined) {
                                clearTimeout(timer);
                                timer = null;
                            }

                            if (val.length < options.minTermLength) {
                                var el = $('[name=' + options.paramName + ']').filter("input[type=hidden]:not([disabled=true]), :enabled, [disabled=false]");
                                var hasSelection = false;
                                if ($(el).is("select")) {
                                    hasSelection = $(el).find("option:selected").filter(function(){ return $(this).val() !== ""; }).length > 0;
                                } else if ($(el).is("input[type=checkbox], input[type=radio]")) {
                                    hasSelection = $(el).filter(":checked").length > 0;
                                }
                                if (!hasSelection) {
                                    //nothing is actually selected - restore the default option list so this shorter keyword filters
                                    //against the full default set instead of whatever narrower result was left from a deeper search
                                    updateOptions("", options.afterTypeDelay, this);
                                } else if (val.length === 0) {
                                    //remove the non selected option when keyword length is 0 to clean the options.
                                    if ($(el).is("select")) {
                                        $(el).find("option:not(:selected)").remove();
                                    } else if ($(el).is("input[type=checkbox], input[type=radio]")) {
                                        $(el).filter(":not(:checked)").remove();
                                    }
                                    $(element).append('<option value=""></option>');
                                    $(element).trigger("chosen:updated");
                                }
                                return false;
                            }
                            updateOptions(val, options.afterTypeDelay, this);
                        }
                    };

                    //bound to both keyup and input: on some browsers/interactions (e.g. reopening an already-selected
                    //single-select's dropdown), keyup does not reliably fire on the reactivated search field, while
                    //input always does. The val===prevVal guard below dedupes when both fire for the same keystroke.
                    $(chosenContainer).find(".search-field > input, .chosen-search > input").on('keyup input', handleSearchInput);

                    if (options.requestParams !== undefined && options.requestParams.length > 0) {
                        var fields = []; 

                        for (var i = 0; i < options.requestParams.length; i++) {
                            var cf = options.requestParams[i].field;
                            fields.push(cf);                      
                        }

                        for (var i in fields) {
                            FormUtil.setControlField(fields[i]);
                        }

                        var updateDependencyOptions = function() {
                            updateOptions("", options.afterTypeDelay, $(chosenContainer).find('.chosen-single > span'));
                        };

                        for (var i in fields) {
                            $('body').off("change", '[name='+fields[i]+']:not(form)', updateDependencyOptions);
                            $('body').on("change", '[name='+fields[i]+']:not(form)', updateDependencyOptions);
                        }

                        $(element).off("jsection:show." + options.paramName);
                        $(element).on("jsection:show." + options.paramName, updateDependencyOptions);
                    }
   
                    $(element).on("change", function (){
                        $(".chosen-select").chosen({placeholder_text: getPlaceholder()}); 
                        if ($(element).val() === "") {
                            if (!$(chosenContainer).find('.chosen-drop').hasClass("reseted")) {
                                updateOptions("", options.afterTypeDelay, $(chosenContainer).find('.chosen-search > input'));
                                $(chosenContainer).find('.chosen-drop').addClass('reseted');
                            }
                        } else {
                            $(chosenContainer).find('.chosen-drop').removeClass('reseted');
                        }
                        $(element).trigger("chosen:updated");
                    });
                    
                    $(element).on("jsection:hide", function(){
                        $(element).trigger("chosen:updated");
                    });
                    
                    $(element).on("jsection:show", function(){
                        $(element).trigger("chosen:updated");
                    });
                    
                    setTimeout(function(){
                        $(element).append('<option value=""></option>');
                        $(element).trigger("chosen:updated");
                    }, 200);
                } else {
                    $(element).prop('disabled', true).trigger("chosen:updated").prop('disabled', false);
                }
                
                var hidden = $(element).closest(".ui-screen-hidden");
                $(hidden).find(".chosen-container").insertAfter(hidden);
            });
        },
        
        //find the chosen object
        setChosenObject : function(el) {
            for (const key in $(el)[0]) {
                if (key.indexOf("jQuery") !== -1 && $(el)[0][key]["chosen"] !== undefined) {
                    $(el).data("chosen", $(el)[0][key]["chosen"]);
                    break;
                }
            }
        }
    };

    $.fn.ajaxselect = function(method) {

        if (methods[method]) {
            return methods[method].apply(this, Array.prototype.slice.call(arguments, 1));
        } else if (typeof method === 'object' || !method) {
            return methods.init.apply(this, arguments);
        } else {
            $.error('Method ' + method + ' does not exist on jQuery.multiselect');
        }

    };

})(jQuery);
