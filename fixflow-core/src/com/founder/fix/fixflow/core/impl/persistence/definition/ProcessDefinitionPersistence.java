package com.founder.fix.fixflow.core.impl.persistence.definition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.bpmn2.Activity;
import org.eclipse.bpmn2.BaseElement;
import org.eclipse.bpmn2.Bpmn2Package;
import org.eclipse.bpmn2.FlowElement;
import org.eclipse.bpmn2.FlowNode;
import org.eclipse.bpmn2.RootElement;
import org.eclipse.bpmn2.SubProcess;
import org.eclipse.bpmn2.Task;
import org.eclipse.bpmn2.di.BpmnDiPackage;
import org.eclipse.bpmn2.impl.BaseElementImpl;
import org.eclipse.bpmn2.util.Bpmn2ResourceFactoryImpl;
import org.eclipse.dd.dc.DcPackage;
import org.eclipse.dd.di.DiPackage;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.impl.EPackageRegistryImpl.Delegator;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.FeatureMap;

import com.founder.fix.bpmn2extensions.fixflow.FixFlowPackage;
import com.founder.fix.fixflow.core.exception.FixFlowException;
import com.founder.fix.fixflow.core.impl.Context;
import com.founder.fix.fixflow.core.impl.ProcessDefinitionQueryImpl;
import com.founder.fix.fixflow.core.impl.bpmn.behavior.DataVariableBehavior;
import com.founder.fix.fixflow.core.impl.bpmn.behavior.DefinitionsBehavior;
import com.founder.fix.fixflow.core.impl.bpmn.behavior.ProcessDefinitionBehavior;
import com.founder.fix.fixflow.core.impl.connector.ConnectorDefinition;
import com.founder.fix.fixflow.core.impl.connector.ConnectorParameterInputs;
import com.founder.fix.fixflow.core.impl.connector.ConnectorParameterOutputs;
import com.founder.fix.fixflow.core.impl.datavariable.DataVariableMgmtDefinition;
import com.founder.fix.fixflow.core.impl.db.PersistentObject;
import com.founder.fix.fixflow.core.impl.db.SqlCommand;
import com.founder.fix.fixflow.core.impl.event.BaseElementEventImpl;
import com.founder.fix.fixflow.core.impl.persistence.deployer.DeploymentCache;
import com.founder.fix.fixflow.core.impl.util.EMFExtensionUtil;
import com.founder.fix.fixflow.core.impl.util.ReflectUtil;
import com.founder.fix.fixflow.core.impl.util.StringUtil;

public class ProcessDefinitionPersistence {

	public Connection connection;
	protected SqlCommand sqlCommand;

	public ProcessDefinitionPersistence(Connection connection) {
		this.connection = connection;
		// 初始化数据库操作类
		sqlCommand = new SqlCommand(connection);
	}

	public void insertProcessDefinition(PersistentObject persistentObject) {
		Map<String, Object> resourceMap = persistentObject.getPersistentState();

		// 构建查询参数
		Map<String, Object> objectParam = new HashMap<String, Object>();

		objectParam.put("PROCESS_ID", resourceMap.get("processDefinitionId"));
		objectParam.put("PROCESS_NAME", resourceMap.get("processDefinitionName"));
		objectParam.put("PROCESS_KEY", resourceMap.get("processDefinitionKey"));
		objectParam.put("CATEGORY", resourceMap.get("category"));
		objectParam.put("VERSION", resourceMap.get("version"));
		objectParam.put("RESOURCE_NAME", resourceMap.get("resourceName"));
		objectParam.put("DEPLOYMENT_ID", resourceMap.get("deploymentId"));
		// objectParam.put("START_FORM_KEY", resourceMap.get("startForm"));
		objectParam.put("RESOURCE_ID", resourceMap.get("resourceId"));

		// 执行插入语句
		sqlCommand.insert("FIXFLOW_DEF_PROCESSDEFINITION", objectParam);
	}

	public ProcessDefinitionBehavior selectLatestProcessDefinitionByKey(String processDefinitionKey) {

		String sqlText = "select * " + "from FIXFLOW_DEF_PROCESSDEFINITION " + "where PROCESS_KEY = ? and "
				+ "VERSION = (select max(VERSION) from FIXFLOW_DEF_PROCESSDEFINITION where PROCESS_KEY = ?)";

		// 构建查询参数

		List<Object> objectParamWhere = new ArrayList<Object>();
		objectParamWhere.add(processDefinitionKey);
		objectParamWhere.add(processDefinitionKey);

		List<Map<String, Object>> dataObj = sqlCommand.queryForList(sqlText, objectParamWhere);
		if (dataObj == null || dataObj.size() == 0) {
			return null;
		}
		Map<String, Object> dataMap = dataObj.get(0);

		String processId = StringUtil.getString(dataMap.get("PROCESS_ID"));
		String deploymentId = StringUtil.getString(dataMap.get("DEPLOYMENT_ID"));
		String resourceName = StringUtil.getString(dataMap.get("RESOURCE_NAME"));
		String category = StringUtil.getString(dataMap.get("CATEGORY"));
		int version = StringUtil.getInt(dataMap.get("VERSION"));
		String resourceId = StringUtil.getString(dataMap.get("RESOURCE_ID"));
		String processKey = StringUtil.getString(dataMap.get("PROCESS_KEY"));
		// String startFormKey =
		// StringUtil.getString(dataMap.get("START_FORM_KEY"));
		DeploymentCache deploymentCache = Context.getProcessEngineConfiguration().getDeploymentCache();
		ProcessDefinitionBehavior processDefinition = deploymentCache.getProcessDefinitionCache().get(processId);
		if (processDefinition == null) {
			processDefinition = getProcessDefinition(deploymentId, resourceName, processKey, processId);
			processDefinition.setProcessDefinitionId(processId);
			processDefinition.setDeploymentId(deploymentId);
			processDefinition.setResourceName(resourceName);
			processDefinition.setCategory(category);
			processDefinition.setVersion(version);
			processDefinition.setResourceId(resourceId);
			// processDefinition.setStartFormKey(startFormKey);
			deploymentCache.addProcessDefinition(processDefinition);
		}

		return processDefinition;

	}

	// selectProcessDefinitionById

	public ProcessDefinitionBehavior selectProcessDefinitionById(String processDefinitionId) {

		DeploymentCache deploymentCache = Context.getProcessEngineConfiguration().getDeploymentCache();
		ProcessDefinitionBehavior processDefinition = deploymentCache.getProcessDefinitionCache().get(
				processDefinitionId);
		if (processDefinition == null) {

			String sqlText = "select * " + "from FIXFLOW_DEF_PROCESSDEFINITION " + "where PROCESS_ID = ?";

			// 构建查询参数

			List<Object> objectParamWhere = new ArrayList<Object>();
			objectParamWhere.add(processDefinitionId);

			List<Map<String, Object>> dataObj = sqlCommand.queryForList(sqlText, objectParamWhere);
			if (dataObj.size() == 0) {
				throw new FixFlowException("流程定义 " + processDefinitionId + " 未查询到!");
			}
			Map<String, Object> dataMap = dataObj.get(0);

			String processId = StringUtil.getString(dataMap.get("PROCESS_ID"));
			String deploymentId = StringUtil.getString(dataMap.get("DEPLOYMENT_ID"));
			String resourceName = StringUtil.getString(dataMap.get("RESOURCE_NAME"));
			String category = StringUtil.getString(dataMap.get("CATEGORY"));
			int version = StringUtil.getInt(dataMap.get("VERSION"));
			String resourceId = StringUtil.getString(dataMap.get("RESOURCE_ID"));
			String processKey = StringUtil.getString(dataMap.get("PROCESS_KEY"));
			// String startFormKey =
			// StringUtil.getString(dataMap.get("START_FORM_KEY"));

			processDefinition = getProcessDefinition(deploymentId, resourceName, processKey, processId);

			processDefinition.setProcessDefinitionId(processId);
			processDefinition.setDeploymentId(deploymentId);
			processDefinition.setResourceName(resourceName);
			processDefinition.setCategory(category);
			processDefinition.setVersion(version);
			processDefinition.setResourceId(resourceId);

			// processDefinition.setStartFormKey(startFormKey);
			deploymentCache.addProcessDefinition(processDefinition);
		}

		return processDefinition;

	}

	public List<ProcessDefinitionBehavior> selectProcessDefinitionsByQueryCriteria(
			ProcessDefinitionQueryImpl processDefinitionQuery) {

		List<Object> objectParamWhere = new ArrayList<Object>();

		String selectProcessDefinitionsByQueryCriteriaSql = " select PD.* from FIXFLOW_DEF_PROCESSDEFINITION PD ";

		selectProcessDefinitionsByQueryCriteriaSql = selectProcessDefinitionsByQueryCriteriaSql + " WHERE 1=1";

		if (processDefinitionQuery.getId() != null) {
			selectProcessDefinitionsByQueryCriteriaSql = selectProcessDefinitionsByQueryCriteriaSql
					+ " and PD.PROCESS_ID=? ";
			objectParamWhere.add(processDefinitionQuery.getId());
		}

		if (processDefinitionQuery.getKey() != null) {
			selectProcessDefinitionsByQueryCriteriaSql = selectProcessDefinitionsByQueryCriteriaSql
					+ " and PD.PROCESS_KEY=? ";
			objectParamWhere.add(processDefinitionQuery.getKey());
		}

		if (processDefinitionQuery.isLatest()) {
			// and PD.VERSION_ = (select max(VERSION_) from ACT_RE_PROCDEF where
			// KEY_ = PD.KEY_)
			selectProcessDefinitionsByQueryCriteriaSql = selectProcessDefinitionsByQueryCriteriaSql
					+ " and PD.VERSION = (select max(VERSION) from FIXFLOW_DEF_PROCESSDEFINITION where PROCESS_KEY = PD.PROCESS_KEY)";
			// objectParamWhere.add(processDefinitionQuery.getKey());
		}

		if (processDefinitionQuery.getOrderBy() != null) {

			selectProcessDefinitionsByQueryCriteriaSql = selectProcessDefinitionsByQueryCriteriaSql + " order by "
					+ processDefinitionQuery.getOrderBy().toString();
		}

		List<Map<String, Object>> dataObj = sqlCommand.queryForList(selectProcessDefinitionsByQueryCriteriaSql,
				objectParamWhere);

		List<ProcessDefinitionBehavior> processDefinitionList = new ArrayList<ProcessDefinitionBehavior>();

		for (Map<String, Object> dataMap : dataObj) {

			String processId = StringUtil.getString(dataMap.get("PROCESS_ID"));
			String deploymentId = StringUtil.getString(dataMap.get("DEPLOYMENT_ID"));
			String resourceName = StringUtil.getString(dataMap.get("RESOURCE_NAME"));
			String category = StringUtil.getString(dataMap.get("CATEGORY"));
			int version = StringUtil.getInt(dataMap.get("VERSION"));
			String resourceId = StringUtil.getString(dataMap.get("RESOURCE_ID"));
			String processKey = StringUtil.getString(dataMap.get("PROCESS_KEY"));

			// String startFormKey =
			// StringUtil.getString(dataMap.get("START_FORM_KEY"));

			DeploymentCache deploymentCache = Context.getProcessEngineConfiguration().getDeploymentCache();
			ProcessDefinitionBehavior processDefinition = deploymentCache.getProcessDefinitionCache().get(processId);
			if (processDefinition == null) {
				processDefinition = getProcessDefinition(deploymentId, resourceName, processKey, processId);

				processDefinition.setResourceId(resourceId);
				processDefinition.setProcessDefinitionId(processId);
				processDefinition.setDeploymentId(deploymentId);
				processDefinition.setResourceName(resourceName);
				processDefinition.setCategory(category);
				processDefinition.setVersion(version);

				// processDefinition.setStartFormKey(startFormKey);

				deploymentCache.addProcessDefinition(processDefinition);
			}

			processDefinitionList.add(processDefinition);
		}

		return processDefinitionList;

	}

	private ProcessDefinitionBehavior getProcessDefinition(String deploymentId, String resourceName, String processKey,
			String processId) {

		String sqlText = "SELECT BYTES FROM FIXFLOW_DEF_BYTEARRAY WHERE NAME=? and DEPLOYMENT_ID=?";

		// 构建查询参数

		List<Object> objectParamWhere = new ArrayList<Object>();
		objectParamWhere.add(resourceName);
		objectParamWhere.add(deploymentId);

		List<Map<String, Object>> dataObj = sqlCommand.queryForList(sqlText, objectParamWhere);
		Map<String, Object> dataMap = dataObj.get(0);

		Object bytesObject = dataMap.get("BYTES");
		
		
		if (bytesObject != null) {
			byte[] bytes = (byte[]) bytesObject;

			Bpmn2ResourceFactoryImpl ddd = new Bpmn2ResourceFactoryImpl();
			String filePath=this.getClass().getClassLoader().getResource("com/founder/fix/fixflow/expand/config/fixflowfile.bpmn").toString();
			Resource ddddResource = null;
			if(!filePath.startsWith("jar")){
				try {
					filePath= java.net.URLDecoder.decode(ReflectUtil.getResource("com/founder/fix/fixflow/expand/config/fixflowconfig.xml").getFile(),"utf-8");
				} catch (UnsupportedEncodingException e) {
					// TODO 自动生成的 catch 块
					e.printStackTrace();
					throw new FixFlowException("流程定义文件加载失败！",e);
				}
				ddddResource = ddd.createResource(URI.createFileURI(filePath));
			}else{
				ddddResource = ddd.createResource(URI.createURI(filePath));
			}
			//Resource ddddResource = ddd.createResource(URI.createFileURI(ReflectUtil.getResource(
					//"com/founder/fix/fixflow/expand/config/fixflowfile.bpmn").getFile()));

			try {
				ddddResource.load(new ByteArrayInputStream(bytes), null);
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				throw new FixFlowException("定义文件加载失败!", e);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				throw new FixFlowException("定义文件加载失败!", e);
			}

			DefinitionsBehavior definitions = (DefinitionsBehavior) ddddResource.getContents().get(0).eContents()
					.get(0);
			definitions.setProcessId(processId);
			ProcessDefinitionBehavior process = null;
			for (RootElement rootElement : definitions.getRootElements()) {
				if (rootElement instanceof ProcessDefinitionBehavior) {
					ProcessDefinitionBehavior processObj = (ProcessDefinitionBehavior) rootElement;
					if (processObj.getProcessDefinitionKey().equals(processKey)) {
						process = (ProcessDefinitionBehavior) rootElement;
						break;
					}

				}
			}
			process.setDefinitions(definitions);

			// 加载事件定义.
			loadEvent(process);
			// 加载数据变量
			loadVariable(process);
			// 设置FlowNode元素的子流程
			loadSubProcess(process);
			return process;

		}
		return null;

	}

	private ProcessDefinitionBehavior getProcessDefinitionNew(String deploymentId, String resourceName,
			String processKey) {

		String sqlText = "SELECT BYTES FROM FIXFLOW_DEF_BYTEARRAY WHERE NAME=? and DEPLOYMENT_ID=?";

		// 构建查询参数

		List<Object> objectParamWhere = new ArrayList<Object>();
		objectParamWhere.add(resourceName);
		objectParamWhere.add(deploymentId);

		List<Map<String, Object>> dataObj = sqlCommand.queryForList(sqlText, objectParamWhere);
		Map<String, Object> dataMap = dataObj.get(0);

		Object bytesObject = dataMap.get("BYTES");
		if (bytesObject != null) {
			byte[] bytes = (byte[]) bytesObject;

			Bpmn2ResourceFactoryImpl ddd = new Bpmn2ResourceFactoryImpl();
			Resource ddddResource = ddd.createResource(URI.createFileURI(ReflectUtil.getResource(
					"com/founder/fix/fixflow/expand/config/fixflowfile.bpmn").getFile()));

			((Delegator) EPackage.Registry.INSTANCE).put("http://www.omg.org/spec/BPMN/20100524/MODEL",
					Bpmn2Package.eINSTANCE);
			((Delegator) EPackage.Registry.INSTANCE).put("http://www.founderfix.com/fixflow", FixFlowPackage.eINSTANCE);
			((Delegator) EPackage.Registry.INSTANCE).put("http://www.omg.org/spec/DD/20100524/DI", DiPackage.eINSTANCE);
			((Delegator) EPackage.Registry.INSTANCE).put("http://www.omg.org/spec/DD/20100524/DC", DcPackage.eINSTANCE);
			((Delegator) EPackage.Registry.INSTANCE).put("http://www.omg.org/spec/BPMN/20100524/DI",
					BpmnDiPackage.eINSTANCE);

			try {
				ddddResource.load(new ByteArrayInputStream(bytes), null);
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				throw new FixFlowException("定义文件加载失败!", e);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				throw new FixFlowException("定义文件加载失败!", e);
			}

			DefinitionsBehavior definitions = (DefinitionsBehavior) ddddResource.getContents().get(0).eContents()
					.get(0);

			ProcessDefinitionBehavior process = null;
			for (RootElement rootElement : definitions.getRootElements()) {
				if (rootElement instanceof ProcessDefinitionBehavior) {
					ProcessDefinitionBehavior processObj = (ProcessDefinitionBehavior) rootElement;
					if (processObj.getProcessDefinitionKey().equals(processKey)) {
						process = (ProcessDefinitionBehavior) rootElement;
						break;
					}

				}
			}
			process.setDefinitions(definitions);

			// 加载事件定义.
			loadEvent(process);
			// 加载数据变量
			loadVariable(process);
			// 设置FlowNode元素的子流程
			loadSubProcess(process);
			return process;

		}
		return null;

	}

	private void setSubProcess(SubProcess subProcess) {
		for (FlowElement flowElement : subProcess.getFlowElements()) {
			if (flowElement instanceof FlowNode) {
				FlowNode flowNode = (FlowNode) flowElement;
				flowNode.setSubProcess(subProcess);
				if (flowElement instanceof SubProcess) {
					setSubProcess(subProcess);
				}
			}

		}
	}

	private void loadSubProcess(ProcessDefinitionBehavior process) {
		for (FlowElement flowElement : process.getFlowElements()) {
			if (flowElement instanceof SubProcess) {
				// FlowNode flowNode=(FlowNode)flowElement;
				setSubProcess((SubProcess) flowElement);
			}
		}
	}

	private void loadVariable(ProcessDefinitionBehavior process) {

		process.setDataVariableMgmtDefinition(new DataVariableMgmtDefinition(process));

		addVariable(process, process, true);

		for (FlowElement flowElement : process.getFlowElements()) {

			if (flowElement instanceof SubProcess) {
				addVariable(flowElement, process, false);
				addSubProcessElement((SubProcess) flowElement, process);

			} else {
				addVariable(flowElement, process, false);
			}

		}

	}

	private void addVariable(BaseElement baseElement, ProcessDefinitionBehavior process, boolean isPubilc) {
		List<FeatureMap.Entry> entryList = EMFExtensionUtil.getExtensionElements(baseElement, "dataVariable");
		if (entryList == null) {
			return;
		}
		for (FeatureMap.Entry entry : entryList) {
			process.getDataVariableMgmtDefinition().addDataVariableBehavior(
					new DataVariableBehavior(entry, baseElement.getId(), isPubilc));
		}
	}

	private void addSubProcessElement(SubProcess subProcess, ProcessDefinitionBehavior process) {

		for (FlowElement flowElementSub : subProcess.getFlowElements()) {

			if (flowElementSub instanceof SubProcess) {
				addVariable(subProcess, process, false);
				addSubProcessElement((SubProcess) flowElementSub, process);
			} else {
				addVariable(flowElementSub, process, false);
			}

		}
	}

	private ProcessDefinitionBehavior loadEvent(ProcessDefinitionBehavior processDefinitionBehavior) {

		for (FlowElement flowElement : processDefinitionBehavior.getFlowElements()) {
			if (flowElement instanceof Activity) {

				loadEventObj(flowElement);

			}
		}

		loadEventObj(processDefinitionBehavior);

		return processDefinitionBehavior;
	}

	private void loadEventObj(BaseElement baseElement) {
		BaseElementImpl baseElementImpl = (BaseElementImpl) baseElement;
		
		
		if(baseElement instanceof SubProcess){
			SubProcess subProcess=(SubProcess)baseElement;
			List<FlowElement> flowElements=subProcess.getFlowElements();
			for (FlowElement flowElement : flowElements) {
				if (flowElement instanceof Activity) {

					loadEventObj(flowElement);

				}
			}
	
		}

		List<FeatureMap.Entry> entryList = EMFExtensionUtil.getExtensionElements(baseElementImpl, "connectorInstance");
		for (FeatureMap.Entry entry : entryList) {

			String packageNamesString = EMFExtensionUtil.getExtensionElementAttributeValue(entry, "packageName");
			String classNameString = EMFExtensionUtil.getExtensionElementAttributeValue(entry, "className");
			String eventTypeString = EMFExtensionUtil.getExtensionElementAttributeValue(entry, "eventType");
			String connectorIdString = EMFExtensionUtil.getExtensionElementAttributeValue(entry, "connectorId");
			String connectorInstanceIdString = EMFExtensionUtil.getExtensionElementAttributeValue(entry,
					"connectorInstanceId");
			String connectorInstanceNameString = EMFExtensionUtil.getExtensionElementAttributeValue(entry,
					"connectorInstanceName");
			String errorHandlingString = EMFExtensionUtil.getExtensionElementAttributeValue(entry, "errorHandling");
			String errorCodeString = EMFExtensionUtil.getExtensionElementAttributeValue(entry, "errorCode");
			String documentationString = EMFExtensionUtil.getExtensionElementValue(EMFExtensionUtil
					.getExtensionElementsInEntry(entry, "documentation").get(0));
			
			String skipExpression=null;
			List<FeatureMap.Entry> skipExpressionObj=EMFExtensionUtil.getExtensionElementsInEntry(entry, "skipComment");
			if(skipExpressionObj.size()>0){
				skipExpression=EMFExtensionUtil.getExtensionElementValue(EMFExtensionUtil.getExtensionElementsInEntry(skipExpressionObj.get(0), "expression").get(0));
				//skipExpression=EMFExtensionUtil.getExtensionElementValue(skipExpressionObj.get(0));
			}
			
			ConnectorDefinition connectorDefinition = new ConnectorDefinition();
			
			connectorDefinition.setConnectorId(connectorIdString);
			connectorDefinition.setConnectorInstanceId(connectorInstanceIdString);
			connectorDefinition.setClassName(classNameString);
			connectorDefinition.setConnectorInstanceName(connectorInstanceNameString);
			connectorDefinition.setDocumentation(documentationString);
			connectorDefinition.setErrorCode(errorCodeString);
			connectorDefinition.setErrorHandling(errorHandlingString);
			connectorDefinition.setEventType(eventTypeString);
			connectorDefinition.setPackageName(packageNamesString);
			connectorDefinition.setSkipExpression(skipExpression);
			if (baseElementImpl.getEvents().get(eventTypeString) == null) {
				BaseElementEventImpl flowNodeEventImpl = new BaseElementEventImpl(eventTypeString);
				flowNodeEventImpl.addConnector(connectorDefinition);

				baseElementImpl.addEvent(flowNodeEventImpl);
			} else {
				baseElementImpl.getEvents().get(eventTypeString).addConnector(connectorDefinition);
			}

			List<FeatureMap.Entry> entryListCPI = EMFExtensionUtil.getExtensionElementsInEntry(entry,
					"connectorParameterInputs");

			for (FeatureMap.Entry entryCPIObj : entryListCPI) {

				String idCPIString = EMFExtensionUtil.getExtensionElementAttributeValue(entryCPIObj, "id");
				String nameCPIString = EMFExtensionUtil.getExtensionElementAttributeValue(entryCPIObj, "name");
				String dataTypeCPIString = EMFExtensionUtil.getExtensionElementAttributeValue(entryCPIObj, "dataType");
				String expressionCPIString = EMFExtensionUtil.getExtensionElementValue(EMFExtensionUtil
						.getExtensionElementsInEntry(entryCPIObj, "expression").get(0));
				ConnectorParameterInputs connectorParameterInputs = new ConnectorParameterInputs();
				connectorParameterInputs.setId(idCPIString);
				connectorParameterInputs.setName(nameCPIString);
				connectorParameterInputs.setDataType(dataTypeCPIString);
				connectorParameterInputs.setExpressionText(expressionCPIString);
				connectorDefinition.getConnectorParameterInputs().add(connectorParameterInputs);

			}

			List<FeatureMap.Entry> entryListCPO = EMFExtensionUtil.getExtensionElementsInEntry(entry,
					"connectorParameterOutputs");

			for (FeatureMap.Entry entryCPOObj : entryListCPO) {

				String variableTargetCPIString = EMFExtensionUtil.getExtensionElementAttributeValue(entryCPOObj,
						"variableTarget");
				String expressionCPIString = EMFExtensionUtil.getExtensionElementValue(EMFExtensionUtil
						.getExtensionElementsInEntry(entryCPOObj, "expression").get(0));
				ConnectorParameterOutputs connectorParameterOutputs = new ConnectorParameterOutputs();

				connectorParameterOutputs.setVariableTarget(variableTargetCPIString);

				connectorParameterOutputs.setExpressionText(expressionCPIString);
				connectorDefinition.getConnectorParameterOutputs().add(connectorParameterOutputs);

			}

		}
	}

	public void deleteProcessDefinitionsByDeploymentId(String deploymentId) {
		// 构建Where查询参数
		Object[] objectParamWhere = { deploymentId };
		sqlCommand.delete("FIXFLOW_DEF_PROCESSDEFINITION", " DEPLOYMENT_ID=?", objectParamWhere);
	}

	public List<Map<String, Object>> selectProcessDefinitionGroupKey() {

		String sqlTextString = "SELECT PROCESS_KEY,MAX(PROCESS_NAME) AS PROCESS_NAME,MAX(CATEGORY) AS CATEGORY "
				+ "FROM FIXFLOW_DEF_PROCESSDEFINITION GROUP BY PROCESS_KEY";
		List<Map<String, Object>> listMap = sqlCommand.queryForList(sqlTextString);

		return listMap;
	}

	public ProcessDefinitionBehavior selectLatestProcessDefinitionByKeyAndVersion(Object parameter) {
		@SuppressWarnings("unchecked")
		Map<String, Object> parameters = (Map<String, Object>) parameter;
		String processDefinitionKey = StringUtil.getString(parameters.get("processDefinitionKey"));
		int processDefinitionVersion = StringUtil.getInt(parameters.get("processDefinitionVersion"));

		String sqlText = "select * " + "from FIXFLOW_DEF_PROCESSDEFINITION " + "where PROCESS_KEY = ? AND VERSION=? ";

		// 构建查询参数

		List<Object> objectParamWhere = new ArrayList<Object>();
		objectParamWhere.add(processDefinitionKey);
		objectParamWhere.add(processDefinitionVersion);

		List<Map<String, Object>> dataObj = sqlCommand.queryForList(sqlText, objectParamWhere);
		Map<String, Object> dataMap = dataObj.get(0);

		String processId = StringUtil.getString(dataMap.get("PROCESS_ID"));
		String deploymentId = StringUtil.getString(dataMap.get("DEPLOYMENT_ID"));
		String resourceName = StringUtil.getString(dataMap.get("RESOURCE_NAME"));
		String category = StringUtil.getString(dataMap.get("CATEGORY"));
		int version = StringUtil.getInt(dataMap.get("VERSION"));
		String resourceId = StringUtil.getString(dataMap.get("RESOURCE_ID"));
		String processKey = StringUtil.getString(dataMap.get("PROCESS_KEY"));

		// String startFormKey =
		// StringUtil.getString(dataMap.get("START_FORM_KEY"));
		DeploymentCache deploymentCache = Context.getProcessEngineConfiguration().getDeploymentCache();
		ProcessDefinitionBehavior processDefinition = deploymentCache.getProcessDefinitionCache().get(processId);
		if (processDefinition == null) {
			processDefinition = getProcessDefinition(deploymentId, resourceName, processKey, processId);

			processDefinition.setProcessDefinitionId(processId);
			processDefinition.setDeploymentId(deploymentId);
			processDefinition.setResourceName(resourceName);
			processDefinition.setCategory(category);
			processDefinition.setVersion(version);
			processDefinition.setResourceId(resourceId);

			// processDefinition.setStartFormKey(startFormKey);
			deploymentCache.addProcessDefinition(processDefinition);
		}

		return processDefinition;
	}
}
