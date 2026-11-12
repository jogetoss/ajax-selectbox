package org.joget.marketplace;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListBinder;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListFilterQueryObject;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.lib.SelectBox;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormAjaxOptionsBinder;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormBuilderPalette;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadBinder;
import org.joget.apps.form.model.FormLoadOptionsBinder;
import org.joget.apps.form.model.FormReferenceDataRetriever;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AjaxSelectbox extends SelectBox implements PluginWebSupport, FormBuilderPaletteElement, FormReferenceDataRetriever {
    public static String MESSAGE_PATH = "message/AjaxSelectbox";
    private static final String DATA_SOURCE_BINDER = "binder";
    //used in place of the list id in the nonce when an options binder is the data source
    private static final String BINDER_LIST_MARKER = "__binder__";
    //maximum number of options returned by a search of an options binder, used when "Max results" is empty or invalid
    private static final int DEFAULT_MAX_BINDER_RESULTS = 50;

    private String idField;
    //options binder restored from the encrypted binder data of an AJAX call
    private FormLoadBinder ajaxBinder;
    
    @Override
    public String getName() {
        return "AjaxSelectbox";
    }

    @Override
    public String getVersion() {
        return "8.0.5";
    }

    @Override
    public String getDescription() {
        return AppPluginUtil.getMessage(getName() + ".desc", getClassName(), MESSAGE_PATH);
    }
    
    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<label class='label'>"+getLabel()+"</label><input type='textfield' value='Type to Select' style='color:silver' />";
    }

    @Override
    public String getLabel() {
        return AppPluginUtil.getMessage(getName() + ".label", getClassName(), MESSAGE_PATH);
    }
    
    @Override
    public String getPropertyOptions() {
        String json = AppUtil.readPluginResource(getClass().getName(), "/properties/ajaxselectbox.json", null, true, MESSAGE_PATH);
        return json;
    }
    
    @Override
    public String getFormBuilderCategory() {
        return FormBuilderPalette.CATEGORY_CUSTOM;
    }

    @Override
    public int getFormBuilderPosition() {
        return 350;
    }
    
    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String template = "ajaxSelectbox.ftl";

        // set value
        String[] valueArray = FormUtil.getElementPropertyValues(this, formData);
        List<String> values = Arrays.asList(valueArray);
        dataModel.put("values", values);

        // set options
        Collection<Map> optionMap = getOptionMap(valueArray, null, formData);
        dataModel.put("options", optionMap);

        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        dataModel.put("appId", appDef.getAppId());
        dataModel.put("appVersion", appDef.getVersion().toString());

        String paramName = FormUtil.getElementParameterName(this);
        
        boolean binderSource = isBinderSource();
        String nonceList = SecurityUtil.generateNonce(new String[]{"AjaxSelectbox", appDef.getAppId(), appDef.getVersion().toString(), paramName, binderSource ? BINDER_LIST_MARKER : getPropertyString("listId")}, 1);
        dataModel.put("nonceList", nonceList);

        dataModel.put("dataSource", binderSource ? DATA_SOURCE_BINDER : "datalist");
        //the binder settings (which may hold API tokens) are only ever sent to the browser encrypted
        dataModel.put("binderData", binderSource ? generateBinderData() : "");

        Object requestParamsProperty = getProperty("requestParams");
        if (requestParamsProperty != null && requestParamsProperty instanceof Object[]) {
            String requestJson = "[";
            for (Object param : ((Object[]) requestParamsProperty)) {
                Map paramMap = ((Map)param);

                if (requestJson.length() > 1) {
                    requestJson += ",";
                }
                requestJson += "{";
                requestJson += "param:'" + paramMap.get("param") + "',";
                requestJson += "field:'" + paramMap.get("field") + "',";
                requestJson += "defaultValue:'" + paramMap.get("defaultValue") + "'";
                requestJson += "}";
            }
            requestJson += "]";

            if (requestJson.length() > 2) {
                dataModel.put("requestParams", requestJson);
            }
        }

        String html = FormUtil.generateElementHtml(this, formData, template, dataModel);
        return html;
    }
    
    public Collection<Map> getOptionMap(String[] valueArray, String keyword, FormData formData){
        Map<String, String> options = new LinkedHashMap<String, String>();
        
        if ("true".equalsIgnoreCase(getPropertyString("allowEmpty"))) {
            options.put("", getPropertyString("placeholder"));
        }
        
        boolean hasValues = valueArray != null && valueArray.length > 0 && !(valueArray.length == 1 && valueArray[0].isEmpty());

        if (isBinderSource()) {
            loadBinderOptions(options, valueArray, keyword);
        } else if (keyword != null && !keyword.isEmpty()) {
            addOptions(options, valueArray, formData, keyword, null);
        } else {
            //no active search - always show the default list, regardless of whether a value is already selected
            int number = 0;
            if (!getPropertyString("defaultOptions").isEmpty()) {
                try {
                    number = Integer.parseInt(getPropertyString("defaultOptions"));
                } catch (Exception e) {
                }
            }
            addOptions(options, null, formData, null, number);

            //make sure the currently selected value(s) stay represented even if outside the default subset
            if (hasValues) {
                addOptions(options, valueArray, formData, null, null);
            }
        }
        
        Collection<Map> results = new ArrayList<Map>();
        for (String v : options.keySet()) {
            Map m = new HashMap();
            m.put(FormUtil.PROPERTY_VALUE, v);
            m.put(FormUtil.PROPERTY_LABEL, options.get(v));
            results.add(m);
        }
        
        return results;
    }
    
    protected void addOptions(Map<String, String> options, String[] valueArray, FormData formData, String keyword, Integer size) {
        DataListCollection rows = getDataListCollection(valueArray, formData, keyword, size);
        if (rows != null && !rows.isEmpty()) {
            String displayField = getPropertyString("displayField");
            if (idField != null && displayField != null) {
                Iterator i = rows.iterator();
                while (i.hasNext()) {
                    Object r = i.next();
                    Object idValue = DataListService.evaluateColumnValueFromRow(r, idField);
                    if (idValue == null) {
                        continue; //nothing to submit for this row, and a null here used to break the whole form rendering
                    }
                    Object label = DataListService.evaluateColumnValueFromRow(r, displayField);
                    options.put(idValue.toString(), label != null ? label.toString() : idValue.toString());
                }
            }
        }
    }

    protected boolean isBinderSource() {
        return DATA_SOURCE_BINDER.equals(getPropertyString("dataSource"));
    }

    /**
     * Joget runs an element's options binder by itself when a form is loaded, with no search keyword, so a binder
     * that expects one (e.g. {values[0]} in the Enhanced JSON API URL) would be called with empty placeholders.
     * This element drives its binder through AJAX instead (see loadConfiguredBinder), so it is hidden from that step.
     */
    @Override
    public FormLoadBinder getOptionsBinder() {
        return null;
    }

    //the options binder configured on this element, which getOptionsBinder() deliberately does not hand out
    protected FormLoadBinder loadConfiguredBinder() {
        return super.getOptionsBinder();
    }

    /**
     * Loads the options through the configured options binder (e.g. the Enhanced JSON API Form Options Binder).
     * The binder is given three values: the typed keyword ({values[0]}), the currently selected value(s) joined
     * with ";" ({values[1]}) and the maximum number of rows ({values[2]}), so the API can filter on its side.
     * Nothing is requested while there is neither a keyword nor a selected value.
     */
    protected void loadBinderOptions(Map<String, String> options, String[] valueArray, String keyword) {
        FormLoadBinder binder = (ajaxBinder != null) ? ajaxBinder : loadConfiguredBinder();
        if (!(binder instanceof FormAjaxOptionsBinder)) {
            LogUtil.warn(getClassName(), "Field \"" + getPropertyString("id") + "\": the options binder is missing or does not support AJAX options.");
            return;
        }

        String kw = (keyword == null) ? "" : keyword;
        StringBuilder selected = new StringBuilder();
        if (valueArray != null) {
            for (String v : valueArray) {
                if (v != null && !v.isEmpty()) {
                    if (selected.length() > 0) {
                        selected.append(";");
                    }
                    selected.append(v);
                }
            }
        }
        if (kw.isEmpty() && selected.length() == 0) {
            return;
        }

        int maxResults = getMaxBinderResults();
        FormRowSet rows;
        try {
            //binders such as the Enhanced JSON API put these values into the request URL as they are, so they are
            //URL-encoded here: a space would make the URL invalid and a "&" in what the user typed could add parameters
            rows = ((FormAjaxOptionsBinder) binder).loadAjaxOptions(new String[]{encodeForUrl(kw), encodeForUrl(selected.toString()), String.valueOf(maxResults)});
        } catch (Exception | LinkageError e) {
            //a failing API or an incompatible binder plugin (e.g. a missing library, which is an Error rather than an
            //Exception) must not break the whole form
            LogUtil.error(getClassName(), e, "Unable to load options from binder " + binder.getClass().getName());
            return;
        }

        int added = 0;
        if (rows != null) {
            for (FormRow row : rows) {
                String value = row.getProperty(FormUtil.PROPERTY_VALUE);
                if (value == null || value.isEmpty()) {
                    continue; //an empty option is already provided by "Allow empty"
                }
                //the API may ignore the row limit, so it is enforced here as well
                if (added++ >= maxResults) {
                    break;
                }
                String label = row.getProperty(FormUtil.PROPERTY_LABEL);
                options.put(value, (label == null || label.isEmpty()) ? value : label);
            }
        }
    }

    //the configured "Max results", or the default (50) when it is empty, not a number or lower than 1
    protected int getMaxBinderResults() {
        try {
            int max = Integer.parseInt(getPropertyString("maxResults").trim());
            return (max > 0) ? max : DEFAULT_MAX_BINDER_RESULTS;
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_BINDER_RESULTS;
        }
    }

    //percent-encodes for use in a URL; a space becomes %20 (not "+") so it is also right inside a path segment
    protected String encodeForUrl(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * The options binder settings can hold API tokens, so they are only ever given to the browser
     * encrypted (the same approach Joget uses for its own AJAX options). Empty when that is not possible.
     */
    protected String generateBinderData() {
        FormLoadBinder binder = loadConfiguredBinder();
        if (!(binder instanceof FormBinder)) {
            LogUtil.warn(getClassName(), "Field \"" + getPropertyString("id") + "\": no options binder is configured.");
            return "";
        }
        if (SecurityUtil.getDataEncryption() == null) {
            LogUtil.warn(getClassName(), "Field \"" + getPropertyString("id") + "\": " + AppPluginUtil.getMessage("AjaxSelectbox.binderUnavailable", getClassName(), MESSAGE_PATH));
            return "";
        }
        try {
            JSONObject json = new JSONObject();
            json.put("className", ((FormBinder) binder).getClassName());
            json.put("properties", FormUtil.generatePropertyJsonObject(((FormBinder) binder).getProperties()));
            //the AJAX request is handled by a new instance of this element, so the limit travels (tamper-proof) with the binder
            json.put("maxResults", getMaxBinderResults());
            return URLEncoder.encode(SecurityUtil.encrypt(json.toString()), "UTF-8");
        } catch (Exception | LinkageError e) {
            LogUtil.error(getClassName(), e, "Unable to prepare the options binder data");
            return "";
        }
    }

    /**
     * Restores the options binder from the encrypted data produced by generateBinderData.
     * @return false when the data is invalid or is not an options binder
     */
    protected boolean prepareBinder(String binderData) {
        try {
            JSONObject json = new JSONObject(SecurityUtil.decrypt(URLDecoder.decode(binderData, "UTF-8")));
            PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
            Plugin plugin = pluginManager.getPlugin(json.getString("className"));
            if (!(plugin instanceof FormLoadOptionsBinder) || !(plugin instanceof FormBinder)) {
                return false;
            }
            //Joget's helper reads the "properties" member of the whole {className, properties} object itself
            ((FormBinder) plugin).setProperties(FormUtil.parsePropertyFromJsonObject(json));
            ajaxBinder = (FormLoadBinder) plugin;
            setProperty("dataSource", DATA_SOURCE_BINDER);
            setProperty("maxResults", json.optString("maxResults", ""));
            return true;
        } catch (Exception | LinkageError e) {
            LogUtil.warn(getClassName(), "Invalid options binder data in AJAX request: " + e.getMessage());
            return false;
        }
    }

    protected DataListCollection getDataListCollection(String[] valueArray, FormData formData, String keyword, Integer size) {
        DataListCollection rows = null;
        
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) AppUtil.getApplicationContext().getBean("datalistDefinitionDao");
        DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(getPropertyString("listId"), appDef);
        if (datalistDefinition != null && datalistDefinition.getJson() != null) {
            String json = datalistDefinition.getJson();

            //populate request params
            Object requestParamsProperty = getProperty("requestParams");
            if (formData != null && requestParamsProperty != null && requestParamsProperty instanceof Object[]) {
                Form form = FormUtil.findRootForm(this);

                for (Object param : ((Object[]) requestParamsProperty)) {
                    Map paramMap = ((Map)param);
                    String parameter = (String) paramMap.get("param");
                    String fieldId = (String) paramMap.get("field");
                    String defaultValue = (String) paramMap.get("defaultValue");
                    String[] paramValues = null;
                    String paramValue = "";

                    if (fieldId != null && !fieldId.isEmpty()) {
                        Element field = FormUtil.findElement(fieldId, form, formData);
                        paramValues = FormUtil.getElementPropertyValues(field, formData);
                    }

                    if (!(paramValues != null && paramValues.length > 0)) {
                        paramValues = new String[]{defaultValue};
                    }

                    paramValue = FormUtil.generateElementPropertyValues(paramValues);
                    json = json.replaceAll(StringUtil.escapeRegex("#requestParam."+parameter+"#"), StringUtil.escapeRegex(paramValue));
                }
            }

            DataListService dataListService = (DataListService) AppUtil.getApplicationContext().getBean("dataListService");
            DataList dataList = dataListService.fromJson(json);

            DataListBinder binder = dataList.getBinder();
            idField = getPropertyString("idField");

            if (binder != null) {
                Collection<DataListFilterQueryObject> queries = new ArrayList<DataListFilterQueryObject>();
                DataListFilterQueryObject[] datalistFilter = dataList.getFilterQueryObjects();
                queries.addAll(Arrays.asList(datalistFilter));

                if (idField == null || idField.isEmpty()) {
                    idField = binder.getPrimaryKeyColumnName();
                }
                
                //the query should be `where filter clauses and (existing values or keyword)`
                String query = "";
                List<String> values = new ArrayList<String>();
                if (valueArray != null && valueArray.length > 0 && !(valueArray.length == 1 && valueArray[0].isEmpty())) {
                    for (String v : valueArray) {
                        if (!v.isEmpty()) {
                            if (!query.isEmpty()) {
                                query += ", ";
                            }
                            query += "?";
                            values.add(v);
                        }
                    }
                    if (!query.isEmpty()) {
                        query = binder.getColumnName(idField) + " in (" + query + ")";
                    }
                }
                
                if (keyword != null && !keyword.isEmpty()) {
                    if (!query.isEmpty()) {
                        query += " OR ";
                    }
                    
                    query += binder.getColumnName(getPropertyString("displayField")) + " like ?";
                    values.add("%" + keyword + "%");
                }
                
                if (!query.isEmpty()) {
                    DataListFilterQueryObject queryObject = new DataListFilterQueryObject();
                    queryObject.setOperator("AND");
                    queryObject.setQuery("(" + query + ")");
                    queryObject.setValues(values.toArray(new String[0]));
                    queries.add(queryObject);
                }

                rows = binder.getData(dataList, binder.getProperties(), queries.toArray(new DataListFilterQueryObject[0]), null, null, null, size);
            }
        }
        return rows;
    }
    
    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String action = SecurityUtil.validateStringInput(request.getParameter("action"));
        if (action != null && !action.isEmpty()) {
            
        } else {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            
            String param = SecurityUtil.validateStringInput(request.getParameter("_paramName"));
            String binderData = request.getParameter("_binderData");
            boolean binderRequest = binderData != null && !binderData.isEmpty();
            String listId = binderRequest ? BINDER_LIST_MARKER : SecurityUtil.validateStringInput(request.getParameter("_listId"));

            String nonce = request.getParameter("_n");
            if (!SecurityUtil.verifyNonce(nonce, new String[]{"AjaxSelectbox", appDef.getAppId(), appDef.getVersion().toString(), param, listId})) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            //set propertise
            if (binderRequest) {
                //the binder and its settings come from the encrypted data, not from plain request parameters
                if (!prepareBinder(binderData)) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
            } else {
                setProperty("listId", listId);
                setProperty("idField", SecurityUtil.validateStringInput(request.getParameter("_idField")));
                setProperty("displayField", SecurityUtil.validateStringInput(request.getParameter("_displayField")));
                setProperty("defaultOptions", SecurityUtil.validateStringInput(request.getParameter("_defaultOptions")));
            }
            setProperty("allowEmpty", SecurityUtil.validateStringInput(request.getParameter("_allowEmpty")));

            String valueStr = StringUtil.stripAllHtmlTag(request.getParameter("_values"));
            String[] values = null;
            if (!valueStr.isEmpty()) {
                values = valueStr.split(";");
            }

            String keyword = StringUtil.stripAllHtmlTag(request.getParameter("_keyword"));
            
            Collection<Map> optionMap = getOptionMap(values, keyword, null);
            
            JSONArray jsonArray = new JSONArray();
            if (optionMap != null) {
                for (Map row : optionMap) {
                    jsonArray.put(row);
                }
            }

            try {
                jsonArray.write(response.getWriter());
            } catch (JSONException ex) {
                LogUtil.error(getClass().getName(), ex, "");
            }
        }
    }

    @Override
    public FormRowSet loadFormRows(String[] primaryKeyValues, FormData formData) {
        FormRowSet rowSet = new FormRowSet();
        if (isBinderSource()) {
            return rowSet; //reference data is only available from a datalist
        }
        DataListCollection list = getDataListCollection(primaryKeyValues, formData, null, null);
        if (list != null && !list.isEmpty()) {
            Iterator i = list.iterator();
            while (i.hasNext()) {
                Map item = (Map) i.next();
                FormRow row = new FormRow();
                row.putAll(item);
                rowSet.add(row);
            }
        }
        return rowSet;
    }
}
