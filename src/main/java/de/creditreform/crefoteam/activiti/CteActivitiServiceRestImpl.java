package de.creditreform.crefoteam.activiti;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.creditreform.crefoteam.cte.rest.RestInvoker;
import de.creditreform.crefoteam.cte.rest.RestInvokerConfig;
import de.creditreform.crefoteam.cte.rest.RestInvokerResponse;
import org.activiti.rest.service.api.RestUrls;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeoutException;

public class CteActivitiServiceRestImpl implements CteActivitiService {
    protected final static Logger LOGGER = LoggerFactory.getLogger(CteActivitiServiceRestImpl.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestInvokerConfig activitiRestInvokerConfig;
    private final RestInvokerActiviti restServiceInvoker;

    public CteActivitiServiceRestImpl(RestInvokerConfig activitiRestInvokerConfig) {
        this.activitiRestInvokerConfig = activitiRestInvokerConfig;
        this.restServiceInvoker = new RestInvokerActiviti(activitiRestInvokerConfig.getServiceURL(), activitiRestInvokerConfig.getServiceUser(), activitiRestInvokerConfig.getServicePassword());
    }

    public static String extendsRestUrls(String[] restUrlArray) {
        String[] resultAry = new String[restUrlArray.length + 1];
        resultAry[0] = SERVICE_PATH;
        System.arraycopy(restUrlArray, 0, resultAry, 1, restUrlArray.length);
        return StringUtils.join(resultAry, '/');
    }

    @Override
    public RestInvokerConfig getActivitiRestInvokerConfig() {
        return activitiRestInvokerConfig;
    }

    @Override
    public RestInvokerActiviti getRestServiceInvoker() {
        return restServiceInvoker;
    }

    @Override
    public InputStream getProcessImage(Integer processInstanceId) throws Exception {
        restServiceInvoker.init(Integer.valueOf(REST_TIME_OUT_IN_MILLIS));
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_PROCESS_INSTANCE_DIAGRAM), processInstanceId.toString());
        LOGGER.debug(formatRequest("getProcessImage", "GET", restServiceInvoker, ""));
        InputStream inputStream = restServiceInvoker.invokeGetInputStream();
        LOGGER.debug(formatResponseBody(null, "{<<<BitMap>>>}"));
        return inputStream;
    }

    @Override
    public int signalEventReceived(final String signalName) throws Exception {
        restServiceInvoker.init(Integer.valueOf(REST_TIME_OUT_IN_MILLIS));
        String appendPath = extendsRestUrls(RestUrls.URL_EXECUTION_COLLECTION);
        restServiceInvoker.appendPath(appendPath);

        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.put("action", "signalEventReceived");
        requestNode.put("signalName", signalName);
        String stringEntity = requestNode.toString();

        LOGGER.debug(formatRequest("signalEventReceived", "PUT", restServiceInvoker, stringEntity));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokePut(stringEntity, RestInvoker.CONTENT_TYPE_JSON);
        LOGGER.debug(formatResponseBody(null, restInvokerResponse.getResponseBody()));
        int statusCode = restInvokerResponse.getStatusCode();
        return statusCode;
    }


    /*****************************************    ProcessDefinitions   *****************************************/
    private List<CteActivitiProcessDefinition> listProcessDefinitions() throws Exception {
        List<CteActivitiProcessDefinition> cteActivitiProcessDefinitionList = new ArrayList<>();
        // KEIN restServiceInvoker.init hier!!!
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_PROCESS_DEFINITION_COLLECTION));
        LOGGER.debug(formatRequest("listProcessDefinitions", "GET", restServiceInvoker, ""));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokeGet(RestInvoker.CONTENT_TYPE_JSON);
        String responseBody = restInvokerResponse.expectStatusOK().getResponseBody();
        LOGGER.debug(formatResponseBody(null, responseBody));
        JsonNode jsonNodeFromResponse = restInvokerResponse.decodeResponse(objectMapper::readTree);
        JsonNode jsonNodeData = jsonNodeFromResponse.get("data");
        Iterator<JsonNode> jsonNodeDataIterator = jsonNodeData.iterator();
        while (jsonNodeDataIterator.hasNext()) {
            jsonNodeData = jsonNodeDataIterator.next();
            CteActivitiProcessDefinition cteActivitiProcessDefinition = new CteActivitiProcessDefinitionRestImpl(jsonNodeData);
            cteActivitiProcessDefinitionList.add(cteActivitiProcessDefinition);
        }
        return cteActivitiProcessDefinitionList;
    }

    public List<CteActivitiProcessDefinition> listProcessDefinitionsLike(String processNameLike) throws Exception {
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.queryParam("nameLike", (processNameLike + "%"));
        return listProcessDefinitions();
    }

    /**
     * GET repository/process-definitions
     * http://pc10003926.verband.creditreform.de:8080/activiti-rest/service/repository/process-definitions?name=JUNITTest1Name
     */
    public CteActivitiProcessDefinition getProcessDefinitionForName(String processName) throws Exception {
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.queryParam("name", processName);
        List<CteActivitiProcessDefinition> cteActivitiProcessDefinitions = listProcessDefinitions();
        if (cteActivitiProcessDefinitions.size() == 1) {
            return cteActivitiProcessDefinitions.get(0);
        } else if (cteActivitiProcessDefinitions.size() > 1) {
            throw new IllegalStateException("ACTIVITI liefert für '" + processName + "' mehrere Prozesse!");
        }
        return null;
    }

    /**
     * GET repository/process-definitions/{processDefinitionId}
     * http://pc10003926.verband.creditreform.de:8080/activiti-rest/service/repository/process-definitions?key=JUNITTest1Key
     */
    public CteActivitiProcessDefinition getProcessDefinitionForKey(String processDefKey) throws Exception {
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_PROCESS_DEFINITION_COLLECTION));
        restServiceInvoker.queryParam("key", processDefKey);
        LOGGER.debug(formatRequest("getProcessDefinitionForKey", "GET", restServiceInvoker, ""));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokeGet(RestInvoker.CONTENT_TYPE_JSON);
        String responseBody = restInvokerResponse.expectStatusOK().getResponseBody();
        LOGGER.debug(formatResponseBody(null, responseBody));
        JsonNode jsonNodeFromResponse = restInvokerResponse.decodeResponse(objectMapper::readTree);
        JsonNode jsonNodeData = jsonNodeFromResponse.get("data");
        Iterator<JsonNode> jsonNodeDataIterator = jsonNodeData.iterator();
        if (jsonNodeDataIterator.hasNext()) {
            jsonNodeData = jsonNodeDataIterator.next();
            CteActivitiProcessDefinition cteActivitiProcessDefinition = new CteActivitiProcessDefinitionRestImpl(jsonNodeData);
            return cteActivitiProcessDefinition;
        }
        return null;
    }
    /*----------------------------------------    ProcessDefinitions   ----------------------------------------*/

    /*****************************************    ProcessInstances   *****************************************/
    @Override
    public List<CteActivitiProcess> queryProcessInstances(String processDefinitionKey, Map<String, Object> paramsMap) throws Exception {
        // POST query/process-instances
        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.put("processDefinitionKey", processDefinitionKey);
        requestNode.set("variables", buildVariablesArrayWithOperation(paramsMap));
        String stringEntity = requestNode.toString();

        List<CteActivitiProcess> cteActivitiProcessInstanceList = new ArrayList<>();
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_PROCESS_INSTANCE_QUERY));
        LOGGER.debug(formatRequest("queryProcessInstances", "POST", restServiceInvoker, stringEntity));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokePost(stringEntity, RestInvoker.CONTENT_TYPE_JSON);
        String responseBody = restInvokerResponse.expectStatusOK().getResponseBody();
        LOGGER.debug(formatResponseBody(null, responseBody));
        JsonNode jsonNodeFromResponse = restInvokerResponse.decodeResponse(objectMapper::readTree);
        JsonNode jsonNodeData = jsonNodeFromResponse.get("data");
        Iterator<JsonNode> jsonNodeDataIterator = jsonNodeData.iterator();
        while (jsonNodeDataIterator.hasNext()) {
            jsonNodeData = jsonNodeDataIterator.next();
            CteActivitiProcess cteActivitiProcessInstance = createCteActivitiProcessImpl(jsonNodeData);
            cteActivitiProcessInstanceList.add(cteActivitiProcessInstance);
        }
        return cteActivitiProcessInstanceList;
    }

    public void deleteProcessInstances(String processDefinitionKey, String meinKey) throws Exception {
        Map<String, Object> paramsMap = new HashMap<>();
        if (processDefinitionKey == null || processDefinitionKey.isEmpty()) {
            Assert.fail("Parameter <processDefinitionKey> darf nicht leer sein!");
        }
        if (meinKey == null || meinKey.isEmpty()) {
            Assert.fail("Parameter <meinKey> darf nicht leer sein!");
        }
        paramsMap.put(ActivitProcessConstants.UT_TASK_PARAM_NAME_MEIN_KEY, meinKey);
        List<CteActivitiProcess> processInstancesList = queryProcessInstances(processDefinitionKey, paramsMap);
        for (CteActivitiProcess processInstance : processInstancesList) {
            deleteProcessInstance(processInstance.getId());
        }
    }

    public void deleteProcessInstance(Integer processInstanceID) throws Exception {
        // DELETE runtime/process-instances/{processInstanceId}
        // DELETE http://pc10003926.verband.creditreform.de:8080/activiti-rest/service/runtime/process-instances/248014
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_PROCESS_INSTANCE), processInstanceID.toString());
        LOGGER.debug(formatRequest("deleteProcessInstance", "DELETE", restServiceInvoker, ""));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokeDelete(RestInvoker.CONTENT_TYPE_JSON);
        LOGGER.debug(formatResponseBody(null, restInvokerResponse.getResponseBody()));
        restInvokerResponse.expectStatusNoContent();
    }
    /*--------------------------------    ProcessInstances   *****************************************/

    public List<CteActivitiExecution> listExecutions() throws Exception {
        // GET runtime/executions
        return getExecutions(null);
    }

    public List<CteActivitiExecution> getExecutions(Integer processInstanceID) throws Exception {
        // GET runtime/executions
        List<CteActivitiExecution> cteActivitiExecutionList = new ArrayList<CteActivitiExecution>();
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_EXECUTION_COLLECTION));
        if (processInstanceID != null) {
            restServiceInvoker.queryParam("processInstanceId", processInstanceID.toString());
        }
        LOGGER.debug(formatRequest("getExecutions", "GET", restServiceInvoker, "processInstanceId = " + processInstanceID));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokeGet(RestInvoker.CONTENT_TYPE_JSON);
        String responseBody = restInvokerResponse.expectStatusOK().getResponseBody();
        LOGGER.debug(formatResponseBody(null, responseBody));
        JsonNode jsonNodeFromResponse = restInvokerResponse.decodeResponse(objectMapper::readTree);
        JsonNode jsonNodeData = jsonNodeFromResponse.get("data");
        Iterator<JsonNode> jsonNodeDataIterator = jsonNodeData.iterator();
        while (jsonNodeDataIterator.hasNext()) {
            jsonNodeData = jsonNodeDataIterator.next();
            CteActivitiExecution cteActivitiExecution = new CteActivitiExecutionRestImpl(jsonNodeData);
            cteActivitiExecutionList.add(cteActivitiExecution);
        }
        return cteActivitiExecutionList;
    }

    protected Integer startProcess(String processDefinitionKey, String meinKey) throws Exception {
        Map<String, Object> paramsMap = new HashMap<>();
        paramsMap.put(ActivitProcessConstants.UT_TASK_PARAM_NAME_MEIN_KEY, meinKey);
        paramsMap.put("includeProcessVariables", "true");
        CteActivitiProcess processInstance = startProcess(processDefinitionKey, paramsMap);
        Assert.assertNotNull(processInstance);
        Map<String, String> variables = processInstance.getVariables();
        String meinKeyX = variables.get(ActivitProcessConstants.UT_TASK_PARAM_NAME_MEIN_KEY);
        Assert.assertEquals(meinKey, meinKeyX);
        Assert.assertNotNull(variables.get("initiator"));

        Integer pInstanceID = processInstance.getId();
        CteActivitiProcess cteActivitiProcessInstance2 = getProcessInstanceByID(pInstanceID);
        Assert.assertNotNull(cteActivitiProcessInstance2);
        Assert.assertEquals(pInstanceID.intValue(), cteActivitiProcessInstance2.getId().intValue());
        return pInstanceID;
    }

    @Override
    public CteActivitiProcess startProcess(String processDefinitionKey, Map<String, Object> paramsMap) throws Exception {
        // POST runtime/process-instances
        if (processDefinitionKey == null) {
            throw new RuntimeException("Für Parameter <processDefinitionKey> muss ein gültiger String-Wert übergeben werden!");
        }
        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.put("processDefinitionKey", processDefinitionKey);
        requestNode.set("variables", buildVariablesArray(paramsMap));
        String stringEntity = requestNode.toString();

        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_PROCESS_INSTANCE_COLLECTION));
        LOGGER.debug(formatRequest("startProcess", "POST", restServiceInvoker, stringEntity));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokePost(stringEntity, RestInvoker.CONTENT_TYPE_JSON);
        String responseBody = restInvokerResponse.getResponseBody();
        LOGGER.debug(formatResponseBody(null, responseBody));
        JsonNode jsonNodeFromResponse = restInvokerResponse.decodeResponse(objectMapper::readTree);
        CteActivitiProcess cteActivitiProcessInstance = createCteActivitiProcessImpl(jsonNodeFromResponse);
        return cteActivitiProcessInstance;
    }

    @Override
    public CteActivitiProcess getProcessInstanceByID(Integer processInstanceID) throws Exception {
        // GET runtime/process-instances
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_PROCESS_INSTANCE), processInstanceID.toString());
        LOGGER.debug(formatRequest("getProcessInstanceByID", "GET", restServiceInvoker, "processInstanceId = \" + processInstanceId"));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokeGet(RestInvoker.CONTENT_TYPE_JSON);
        String responseBody = restInvokerResponse.expectStatusOK().getResponseBody();
        LOGGER.debug(formatResponseBody(null, responseBody));
        JsonNode jsonNodeFromResponse = restInvokerResponse.decodeResponse(objectMapper::readTree);
        CteActivitiProcess cteActivitiProcessInstance = createCteActivitiProcessImpl(jsonNodeFromResponse);
        return cteActivitiProcessInstance;
    }

    @Override
    public List<CteActivitiTask> listTasks(Map<String, Object> paramsMap) throws Exception {
        // GET runtime/tasks
        List<CteActivitiTask> cteActivitiTasksList = new ArrayList<CteActivitiTask>();
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_TASK_COLLECTION));
        if (paramsMap != null) {
            for (Map.Entry<String, Object> paramEntry : paramsMap.entrySet()) {
                restServiceInvoker.queryParam(paramEntry.getKey(), paramEntry.getValue().toString());
            }
        }
        LOGGER.debug(formatRequest("listTasks", "GET", restServiceInvoker, ""));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokeGet(RestInvoker.CONTENT_TYPE_JSON);
        String responseBody = restInvokerResponse.expectStatusOK().getResponseBody();
        LOGGER.debug(formatResponseBody(null, responseBody));
        JsonNode jsonNodeFromResponse = restInvokerResponse.decodeResponse(objectMapper::readTree);
        JsonNode jsonNodeData = jsonNodeFromResponse.get("data");
        Iterator<JsonNode> jsonNodeDataIterator = jsonNodeData.iterator();
        while (jsonNodeDataIterator.hasNext()) {
            jsonNodeData = jsonNodeDataIterator.next();
            try {
                CteActivitiTaskRestImpl cteActivitiTask = createCteActivitiTaskRestImpl(jsonNodeData);
                cteActivitiTasksList.add(cteActivitiTask);
            } catch (Exception ex) {
                CteActivitiTaskRestImpl cteActivitiTask = new CteActivitiTaskRestImpl(jsonNodeData);
                String steErr = String.format("CteActivitiServiceRestImpl#listTasks(): createCteActivitiTaskRestImpl() für %d führt zur Exception:", cteActivitiTask.getId());
                LOGGER.warn(steErr, ex);
            }
        }
        return cteActivitiTasksList;
    }

    protected CteActivitiTask queryNextTaskForTaskVariables(Map<String, Object> paramsMap) throws Exception {
        List<CteActivitiTask> tasksFor = queryTasksForTaskVariables(paramsMap);
        if (tasksFor.size() > 1) {
            throw new IllegalStateException("queryNextTaskForTaskVariables(): queryTasksForTaskVariables() liefert mehrere Treffer für Parameter " + paramsMap);
        }
        if (tasksFor.size() == 1) {
            return tasksFor.get(0);
        }
        return null;
    }

    protected List<CteActivitiTask> queryTasksForTaskVariables(Map<String, Object> myVariablesMap) throws Exception {
        // POST activiti-rest/service/query/tasks
        List<CteActivitiTask> cteActivitiTaskList = new ArrayList<>();
        JsonNode jsonNodeData = gueryForTasks(null);
        Iterator<JsonNode> jsonNodeDataIterator = jsonNodeData.iterator();
        while (jsonNodeDataIterator.hasNext()) {
            jsonNodeData = jsonNodeDataIterator.next();
            try {
                CteActivitiTaskRestImpl cteActivitiTaskRest = createCteActivitiTaskRestImpl(jsonNodeData);
                Boolean matches = null;
                Map<String, String> taskVariablesMap = cteActivitiTaskRest.getVariables();
                for (Map.Entry<String, Object> myEntry : myVariablesMap.entrySet()) {
                    matches = Boolean.TRUE;
                    String taskVariable = taskVariablesMap.get(myEntry.getKey());
                    if (taskVariable != null) {
                        String myVariable = (String) myVariablesMap.get(myEntry.getKey());
                        if (myVariable != null) {
                            matches &= myVariable.equals(taskVariable);
                        }
                    }
                    if (!matches) {
                        break;
                    }
                }
                if (matches == null || matches) {
                    cteActivitiTaskList.add(cteActivitiTaskRest);
                }
            } catch (Exception ex) {
                CteActivitiTaskRestImpl cteActivitiTask = new CteActivitiTaskRestImpl(jsonNodeData);
                String steErr = String.format("CteActivitiServiceRestImpl#queryTasksForTaskVariables(): createCteActivitiTaskRestImpl() für %d führt zur Exception:", cteActivitiTask.getId());
                LOGGER.warn(steErr, ex);
            }
        }
        return cteActivitiTaskList;
    }

    @Override
    public void deleteTask(Integer taskID) throws Exception {
        // DELETE runtime/tasks/{taskId}?cascadeHistory={cascadeHistory}&deleteReason={deleteReason}
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_TASK), taskID.toString());
        restServiceInvoker.queryParam("?cascadeHistory", "true");
        LOGGER.debug(formatRequest("deleteTask", "DELETE", restServiceInvoker, ""));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokeDelete(RestInvoker.CONTENT_TYPE_JSON);
        LOGGER.debug(formatResponseBody(null, restInvokerResponse.getResponseBody()));
        restInvokerResponse.expectStatusNoContent();
    }

    @Override
    public CteActivitiTask selectTaskForBusinessKey(Integer processInstanceID, String meinKey) throws Exception {
        LOGGER.info(String.format("\tRestIntegrationTestBase::selectTaskForBusinessKey({})::", meinKey));
        Map<String, Object> myVariablesMap = new HashMap<>();
        myVariablesMap.put(ActivitProcessConstants.UT_TASK_PARAM_NAME_MEIN_KEY, meinKey);
        myVariablesMap.put("PROCESS_ID", processInstanceID);
        myVariablesMap.put("includeProcessVariables", "true");
        final long timeMillis = System.currentTimeMillis();
        CteActivitiTask theUserTask = null;
        while (theUserTask == null) {
            theUserTask = queryNextTaskForTaskVariables(myVariablesMap);
            if (theUserTask != null) {
                break;
            }
            Thread.sleep(1000);
            LOGGER.info(".");
            if (System.currentTimeMillis() > timeMillis + REST_TIME_OUT_IN_MILLIS) {
                throw new TimeoutException("\n\tqueryNextTaskForTaskVariables() liefert keine Nachfolge-Task innerhalb von " + REST_TIME_OUT_IN_MILLIS + " Sekunden!");
            }
        }
        Map<String, String> variables = theUserTask.getVariables();
        LOGGER.info("\t\tTask:\tID = {}, ProcessInstanceId = {}, ExecutionId = {}}, TaskDefinitionKey = {}\n\t\t\tParameter = {}",
                theUserTask.getId(), theUserTask.getProcessInstanceId(), theUserTask.getExecutionId(), theUserTask.getTaskDefinitionKey(), variables.toString());
        return theUserTask;
    }

    @Override
    public void claimTask(CteActivitiTask cteActivitiTask, String userID) throws Exception {
        Integer id = cteActivitiTask.getId();
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_TASK), id.toString());

        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.put("action", "claim");
        requestNode.put("assignee", userID);
        String stringEntity = requestNode.toString();
        LOGGER.debug(formatRequest("claimTask", "POST", restServiceInvoker, stringEntity));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokePost(stringEntity, RestInvoker.CONTENT_TYPE_JSON);
        LOGGER.debug(formatResponseBody(null, restInvokerResponse.getResponseBody()));
        // stelle sicher, dass der Status-Code der Response 200 ist
        restInvokerResponse.expectStatusOK();
    }

    @Override
    public void unclaimTask(CteActivitiTask cteActivitiTask) throws Exception {
        Integer id = cteActivitiTask.getId();
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_TASK), id.toString());

        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.put("action", "claim");
        String stringEntity = requestNode.toString();
        LOGGER.debug(formatRequest("unclaimTask", "POST", restServiceInvoker, stringEntity));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokePost(stringEntity, RestInvoker.CONTENT_TYPE_JSON);
        LOGGER.debug(formatResponseBody(null, restInvokerResponse.getResponseBody()));
        // stelle sicher, dass der Status-Code der Response 200 ist
        restInvokerResponse.expectStatusOK();
    }

    @Override
    public void completeTask(CteActivitiTask cteActivitiTask, Map<String, Object> taskParams) throws Exception {
        Integer id = cteActivitiTask.getId();
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_TASK), id.toString());
        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.put("action", "complete");
        requestNode.set("variables", buildVariablesArray(taskParams));
        String stringEntity = requestNode.toString();
        LOGGER.debug(formatRequest("completeTask", "POST", restServiceInvoker, stringEntity));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokePost(stringEntity, RestInvoker.CONTENT_TYPE_JSON);
        LOGGER.debug(formatResponseBody(null, restInvokerResponse.getResponseBody()));
        // stelle sicher, dass der Status-Code der Response 201 ist
        restInvokerResponse.expectStatusOK();
    }

    protected Map<String, String> getProcessVariables(Integer processInstanceId) throws Exception {
        // http://pc10003926.verband.creditreform.de:8080/activiti-rest/service/runtime/process-instances/269881/variables
        Map<String, String> processVars = new HashMap<>();
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_PROCESS_INSTANCE_VARIABLE_COLLECTION), processInstanceId.toString());
        LOGGER.debug(formatRequest("getProcessVariables", "GET", restServiceInvoker, "processInstanceId = " + processInstanceId));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokeGet(RestInvoker.CONTENT_TYPE_JSON);
        String responseBody = restInvokerResponse.expectStatusOK().getResponseBody();
        LOGGER.debug(formatResponseBody(null, "\"variables\":" + responseBody));
        JsonNode jsonNodeFromResponse = restInvokerResponse.decodeResponse(objectMapper::readTree);
        Iterator<JsonNode> jsonNodeDataIterator = jsonNodeFromResponse.iterator();
        while (jsonNodeDataIterator.hasNext()) {
            jsonNodeFromResponse = jsonNodeDataIterator.next();
            CteActivitiVariable cteActivitiVariable = new CteActivitiVariableImpl(jsonNodeFromResponse);
            processVars.put(cteActivitiVariable.getName(), cteActivitiVariable.getValue());
        }
        return processVars;
    }

    protected Map<String, String> getTaskVariables(Integer taskId) throws Exception {
        // GET runtime/tasks/{taskId}/variables?scope={scope}
        Map<String, String> processVars = new HashMap<>();
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_TASK_VARIABLES_COLLECTION), taskId.toString());
        LOGGER.debug(formatRequest("getTaskVariables", "GET", restServiceInvoker, "taskId = " + taskId));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokeGet(RestInvoker.CONTENT_TYPE_JSON);
        String responseBody = restInvokerResponse.expectStatusOK().getResponseBody();
        LOGGER.debug(formatResponseBody(null, "\"variables\":" + responseBody));
        JsonNode jsonNodeFromResponse = restInvokerResponse.decodeResponse(objectMapper::readTree);
        Iterator<JsonNode> jsonNodeDataIterator = jsonNodeFromResponse.iterator();
        while (jsonNodeDataIterator.hasNext()) {
            jsonNodeFromResponse = jsonNodeDataIterator.next();
            CteActivitiVariable cteActivitiVariable = new CteActivitiVariableImpl(jsonNodeFromResponse);
            processVars.put(cteActivitiVariable.getName(), cteActivitiVariable.getValue());
        }
        return processVars;
    }

    protected void checkTaskVariables(CteActivitiTask cteActivitiTask, String meinKey2) {
        Map<String, String> variables = cteActivitiTask.getVariables();
        String meinKeyX = variables.get(ActivitProcessConstants.UT_TASK_PARAM_NAME_MEIN_KEY);
        Assert.assertEquals(meinKeyX, meinKey2);
        // ????? warum fehlt die denn manchmal??? Assert.assertNotNull(variables.get("initiator"));
        logUserTask(cteActivitiTask);
    }

    protected JsonNode gueryForTasks(Map<String, Object> paramsMap) throws Exception {
        // POST activiti-rest/service/query/tasks
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_TASK_QUERY));
        ObjectNode requestNode = objectMapper.createObjectNode();
        if (paramsMap != null) {
            for (Map.Entry<String, Object> paramEntry : paramsMap.entrySet()) {
                requestNode.put(paramEntry.getKey(), paramEntry.getValue().toString());
            }
        }
        String stringEntity = requestNode.toString();
        LOGGER.debug(formatRequest("gueryForTasks", "POST", restServiceInvoker, stringEntity));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokePost(stringEntity, RestInvoker.CONTENT_TYPE_JSON);
        String responseBody = restInvokerResponse.expectStatusOK().getResponseBody();
        LOGGER.debug(formatResponseBody(null, responseBody));
        JsonNode rootNode = restInvokerResponse.decodeResponse(objectMapper::readTree);
        return rootNode.get("data");
    }

    protected CteActivitiTaskRestImpl createCteActivitiTaskRestImpl(JsonNode jsonNodeData) throws Exception {
        CteActivitiTaskRestImpl cteActivitiTask = new CteActivitiTaskRestImpl(jsonNodeData);
        // die Variablen des Tasks müssen explizit abgefragt werden!
        Map<String, String> taskVariables = getTaskVariables(cteActivitiTask.getId());
        cteActivitiTask.setVariables(taskVariables);
        return cteActivitiTask;
    }

    protected CteActivitiProcess createCteActivitiProcessImpl(JsonNode jsonNodeData) throws Exception {
        CteActivitiProcess cteActivitiProcessInstance = new CteActivitiProcessRestImpl(jsonNodeData);
        // die Variablen des Prozesses müssen explizit abgefragt werden!
        Map<String, String> processVariables = getProcessVariables(cteActivitiProcessInstance.getId());
        cteActivitiProcessInstance.setVariables(processVariables);
        return cteActivitiProcessInstance;
    }

    private String formatJsonString(String jsonString) {
        if ((jsonString == null) || (jsonString.length() < 1)) {
            jsonString = "{}";
        }
        if (!jsonString.startsWith("{")) {
            jsonString = "{\n" + jsonString;
        }
        if (!jsonString.endsWith("}")) {
            jsonString += "\n}";
        }
        try {
            Object json = objectMapper.readValue(jsonString, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception ex) {
            // dann eben nicht!
        }
        return jsonString;
    }

    private String formatResponseBody(String methodName, String responseBody) {
        String replacedResponseBody = formatJsonString(responseBody);
        if (methodName != null) {
            replacedResponseBody = String.format("\n<<%s>>\nResponse:%s", methodName, replacedResponseBody);
        } else {
            replacedResponseBody = String.format("\nResponse:%s", replacedResponseBody);
        }
        return replacedResponseBody;
    }

    private String formatRequest(String methodName, String httpMethod, RestInvokerActiviti restServiceInvoker, String stringEntity) {
        String line = "===============================================================================================================";
        String formattedJsonString = formatJsonString(stringEntity);
        String formattedRequest = String.format("\n%s\n<<%s>>\nReguest:\n\tMethod:\t%s\n\tURL:\t%s\n\tParams:\t%s",
                line, methodName, httpMethod, restServiceInvoker.buildURI(), formattedJsonString);
        return formattedRequest;
    }

    public File prepareBpmnFileForEnvironment(String bpmnFileName, String envName) throws Exception {
        File srcFile = new File(bpmnFileName);
        File dstFile = new File(System.getProperty("user.dir"), String.format("%s-%s", envName, srcFile.getName()));
        String oldContent = FileUtils.readFileToString(srcFile);
        String newContent = oldContent.replaceAll("%ENV%", envName);
        FileUtils.writeStringToFile(dstFile, newContent);
        return dstFile;
    }

    protected void logUserTask(CteActivitiTask cteActivitiTask) {
        String stringBuffer = "::UserTask::" +
                "\tTaskDefinitionKey: " + cteActivitiTask.getTaskDefinitionKey() +
                "\tID: " + cteActivitiTask.getId() +
                "\tName: " + cteActivitiTask.getName() +
                "\tVariables: " + cteActivitiTask.getVariables() +
                "\n";
        LOGGER.debug(stringBuffer);
    }


    private ArrayNode buildVariablesArray(Map<String, Object> paramsMap) {
        ArrayNode array = objectMapper.createArrayNode();
        if (paramsMap != null) {
            for (Map.Entry<String, Object> entry : paramsMap.entrySet()) {
                ObjectNode varNode = objectMapper.createObjectNode();
                varNode.put("name", entry.getKey());
                varNode.put("value", entry.getValue().toString());
                array.add(varNode);
            }
        }
        return array;
    }

    private ArrayNode buildVariablesArrayWithOperation(Map<String, Object> paramsMap) {
        ArrayNode array = objectMapper.createArrayNode();
        if (paramsMap != null) {
            for (Map.Entry<String, Object> entry : paramsMap.entrySet()) {
                ObjectNode varNode = objectMapper.createObjectNode();
                varNode.put("name", entry.getKey());
                varNode.put("value", entry.getValue().toString());
                varNode.put("operation", "like");
                varNode.put("type", "string");
                array.add(varNode);
            }
        }
        return array;
    }

    /*****************************************    Deployments   *****************************************/
    private List<CteActivitiDeployment> listDeploymentsInternal() throws Exception {
        List<CteActivitiDeployment> cteActivitiDeploymentList = new ArrayList<>();
        // KEIN restServiceInvoker.init hier!!!
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_DEPLOYMENT_COLLECTION));
        LOGGER.debug(formatRequest("listDeploymentsInternal", "GET", restServiceInvoker, ""));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokeGet(RestInvoker.CONTENT_TYPE_JSON);
        String responseBody = restInvokerResponse.expectStatusOK().getResponseBody();
        LOGGER.debug(formatResponseBody(null, responseBody));
        JsonNode jsonNodeFromResponse = restInvokerResponse.decodeResponse(objectMapper::readTree);
        JsonNode jsonNodeData = jsonNodeFromResponse.get("data");
        Iterator<JsonNode> jsonNodeDataIterator = jsonNodeData.iterator();
        while (jsonNodeDataIterator.hasNext()) {
            jsonNodeData = jsonNodeDataIterator.next();
            CteActivitiDeployment cteActivitiDeployment = new CteActivitiDeploymentRestImpl(jsonNodeData);
            cteActivitiDeploymentList.add(cteActivitiDeployment);
        }
        return cteActivitiDeploymentList;
    }

    public List<CteActivitiDeployment> listDeploymentsForNameLike(String deploymentNameLike) throws Exception {
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.queryParam("nameLike", (deploymentNameLike + "%"));
        return listDeploymentsInternal();
    }

    public CteActivitiDeployment getDeploymentForName(String deploymentName) throws Exception {
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.queryParam("name", deploymentName);
        List<CteActivitiDeployment> cteActivitiDeployments = listDeploymentsInternal();
        if (cteActivitiDeployments.size() == 1) {
            return cteActivitiDeployments.get(0);
        } else if (cteActivitiDeployments.size() > 1) {
            throw new IllegalStateException("ACTIVITI liefert für '" + deploymentName + "' mehrere Deployments!");
        }
        return null;
    }

    public String uploadDeploymentFile(File deploymentFile) throws Exception {
        // POST repository/deployments
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_DEPLOYMENT_COLLECTION));
        LOGGER.debug(formatRequest("uploadDeployment", "POST", restServiceInvoker, ""));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokePostMP(deploymentFile, RestInvoker.CONTENT_TYPE_JSON);
        String responseBody = restInvokerResponse.expectStatusCreated().getResponseBody();
        LOGGER.debug(formatResponseBody(null, responseBody));
        JsonNode jsonNodeFromResponse = restInvokerResponse.decodeResponse(objectMapper::readTree);
        String deploymentID = jsonNodeFromResponse.get("id").textValue();
        return deploymentID;
    }

    public void deleteDeploymentForName(String deploymentName) throws Exception {
        CteActivitiDeployment cteActivitiDeploymentForName = getDeploymentForName(deploymentName);
        if (cteActivitiDeploymentForName == null) {
            throw new RuntimeException("Deployment '" + deploymentName + "' existiert nicht!");
        }
        // DELETE repository/deployments/{deploymentId}
        String deploymentId = cteActivitiDeploymentForName.getId();
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_DEPLOYMENT), deploymentId);
        LOGGER.debug(formatRequest("deleteDeployment", "DELETE", restServiceInvoker, ""));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokeDelete(RestInvoker.CONTENT_TYPE_JSON);
        LOGGER.debug(formatResponseBody(null, restInvokerResponse.getResponseBody()));
        restInvokerResponse.expectStatusNoContent();
    }

    public void deleteCteActivitiDeployment(CteActivitiDeployment cteActivitiDeployment) throws Exception {
        // DELETE repository/deployments/{deploymentId}
        String deploymentId = cteActivitiDeployment.getId();
        restServiceInvoker.init(REST_TIME_OUT_IN_MILLIS);
        restServiceInvoker.appendPath(extendsRestUrls(RestUrls.URL_DEPLOYMENT), deploymentId);
        LOGGER.debug(formatRequest("deleteDeployment", "DELETE", restServiceInvoker, ""));
        RestInvokerResponse restInvokerResponse = restServiceInvoker.invokeDelete(RestInvoker.CONTENT_TYPE_JSON);
        LOGGER.debug(formatResponseBody(null, restInvokerResponse.getResponseBody()));
        restInvokerResponse.expectStatusNoContent();
    }
    /*----------------------------------------    Deployments   ----------------------------------------*/


}
