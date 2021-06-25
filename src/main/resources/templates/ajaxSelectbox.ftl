<div class="form-cell" ${elementMetaData!}>

    <#if (element.properties.readonly! == 'true' && element.properties.readonlyLabel! == 'true') >
        <label class="label">${element.properties.label} <span class="form-cell-validator">${decoration}</span><#if error??> <span class="form-error-message">${error}</span></#if></label>
        <div class="form-cell-value">
            <#list options as option>
                <#if values?? && values?seq_contains(option.value!)>
                    <label class="readonly_label">
                        <span>${option.label!?html}</span>
                        <input id="${elementParamName!}" name="${elementParamName!}" type="hidden" value="${option.value!?html}" />
                    </label>
                </#if>
            </#list>
        </div>
        <div style="clear:both;"></div>
    <#else>
        <#if !(request.getAttribute("org.joget.marketplace.AjaxSelectbox_EDITABLE")??) >
            <script type="text/javascript" src="${request.contextPath}/js/chosen/chosen.jquery.js"></script>
            <script type="text/javascript" src="${request.contextPath}/plugin/org.joget.marketplace.AjaxSelectbox/js/jquery.ajaxselect.js"></script>
            <link rel="stylesheet" type="text/css" href="${request.contextPath}/js/chosen/chosen.css" />
        </#if>

        <label class="label">${element.properties.label} <span class="form-cell-validator">${decoration}</span><#if error??> <span class="form-error-message">${error}</span></#if></label>
        <div class='ui-screen-hidden'>
            <select id="${elementParamName!}" name="${elementParamName!}" style="display:none" size="3" <#if element.properties.multiple! == 'true'>multiple</#if> class="multiselect_${element.properties.elementUniqueKey!} <#if error??>form-error-cell</#if>">
                <#list options as option>
                    <#if option.value! != ''>
                        <option value="${option.value!?html}" grouping="${option.grouping!?html}" <#if values?? && values?seq_contains(option.value!)>selected</#if>>${option.label!?html}</option>
                    <#else>
                        <option value="">${option.label!?html}</option>
                    </#if>
                </#list>
            </select>
        </div>

        <script type="text/javascript">
            $(document).ready(function(){
                $("select.multiselect_${element.properties.elementUniqueKey!}").ajaxselect({
                    width: "${element.properties.width!}",
                    readOnly: "${element.properties.readonly!}",
                    paramName : "${elementParamName!}",
                    nonce : "${nonceList!}",
                    appId : "${appId!}",
                    appVersion : "${appVersion!}",
                    listId : "${element.properties.listId!}",
                    idField : "${element.properties.idField!}",
                    displayField : "${element.properties.displayField!}",
                    contextPath : "${request.contextPath}",
                    allowEmpty : "${element.properties.allowEmpty!}",
                    minTermLength : ${element.properties.minTermLength!},
                    keepTypingMsg : "${element.properties.keepTypingMsg!}",
                    lookingForMsg : "${element.properties.lookingForMsg!}",
                    afterTypeDelay : ${element.properties.afterTypeDelay!}
                    <#if requestParams??>, requestParams:${requestParams}</#if>
                });
            });
        </script>
    </#if>
</div>
