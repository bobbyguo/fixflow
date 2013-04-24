package com.founder.fix.fixflow.core.impl.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.bpmn2.FlowNode;
import org.eclipse.bpmn2.SequenceFlow;
import org.eclipse.bpmn2.UserTask;

import com.founder.fix.fixflow.core.impl.Context;
import com.founder.fix.fixflow.core.impl.bpmn.behavior.ProcessDefinitionBehavior;
import com.founder.fix.fixflow.core.impl.bpmn.behavior.TaskCommandInst;
import com.founder.fix.fixflow.core.impl.bpmn.behavior.UserTaskBehavior;
import com.founder.fix.fixflow.core.impl.filter.AbstractCommandFilter;
import com.founder.fix.fixflow.core.impl.persistence.ProcessDefinitionManager;
import com.founder.fix.fixflow.core.impl.runtime.TokenEntity;
import com.founder.fix.fixflow.core.impl.task.TaskInstanceEntity;
import com.founder.fix.fixflow.core.runtime.Token;
import com.founder.fix.fixflow.core.task.TaskInstance;

public class CoreUtil {

	public static List<TaskCommandInst> getTaskCommandInst(TaskInstance taskInstance) {

		ProcessDefinitionManager processDefinitionManager = Context.getCommandContext().getProcessDefinitionManager();
		ProcessDefinitionBehavior processDefinition = processDefinitionManager.findLatestProcessDefinitionById(taskInstance.getProcessDefinitionId());

		return getTaskCommandInst(taskInstance, processDefinition);

	}

	public static List<TaskCommandInst> getTaskCommandInst(TaskInstance taskInstance, ProcessDefinitionBehavior processDefinition) {

		UserTaskBehavior userTask = (UserTaskBehavior) processDefinition.getDefinitions().getElement(taskInstance.getNodeId());

		List<TaskCommandInst> taskCommandInsts = userTask.getTaskCommands();
		List<TaskCommandInst> taskCommandInstsNew = new ArrayList<TaskCommandInst>();
		for (TaskCommandInst taskCommandInst : taskCommandInsts) {
			AbstractCommandFilter abstractCommandFilter = Context.getProcessEngineConfiguration().getAbstractCommandFilterMap()
					.get(taskCommandInst.getTaskCommandType());
			if (abstractCommandFilter != null) {
				if (abstractCommandFilter.accept(taskInstance)) {
					taskCommandInstsNew.add(taskCommandInst);
				}
			} else {
				taskCommandInstsNew.add(taskCommandInst);
			}
		}

		return taskCommandInstsNew;
	}

	public static List<TaskCommandInst> getSubmitNodeTaskCommandInst(UserTaskBehavior userTask) {

		List<TaskCommandInst> taskCommandInsts = userTask.getTaskCommands();
		List<TaskCommandInst> taskCommandInstsNew = new ArrayList<TaskCommandInst>();
		for (TaskCommandInst taskCommandInst : taskCommandInsts) {
			AbstractCommandFilter abstractCommandFilter = Context.getProcessEngineConfiguration().getAbstractCommandFilterMap()
					.get(taskCommandInst.getTaskCommandType());
			if (abstractCommandFilter != null) {
				if (abstractCommandFilter.accept(null)) {
					taskCommandInstsNew.add(taskCommandInst);
				}
			} else {
				taskCommandInstsNew.add(taskCommandInst);
			}
		}

		return taskCommandInstsNew;
	}

	public static Map<String, FlowNode> getBeforeFlowNode(FlowNode flowNode) {

		Map<String, FlowNode> sourceRefFlowNode = new HashMap<String, FlowNode>();

		getBeforeFlowNodeDG(flowNode, sourceRefFlowNode);

		return sourceRefFlowNode;
	}

	private static void getBeforeFlowNodeDG(FlowNode flowNode, Map<String, FlowNode> sourceRefFlowNode) {

		List<SequenceFlow> sequenceFlowList = flowNode.getIncoming();

		for (SequenceFlow sequenceFlow : sequenceFlowList) {
			if (sequenceFlow.getSourceRef() != null) {
				if (sequenceFlow.getSourceRef() instanceof UserTask) {
					// sourceRefFlowNode.add(sequenceFlow.getSourceRef());
					if (sourceRefFlowNode.get(sequenceFlow.getSourceRef().getId()) != null) {
						continue;
					} else {
						sourceRefFlowNode.put(sequenceFlow.getSourceRef().getId(), sequenceFlow.getSourceRef());
					}

				}

				getBeforeFlowNodeDG(sequenceFlow.getSourceRef(), sourceRefFlowNode);
			}

		}

	}

	/**
	 * 获取当前任务之前的可以被退回的任务
	 * @param taskInstance
	 * @return
	 */
	public static List<TaskInstance> getRollBackTask(TaskInstance taskInstance) {

		List<TaskInstance> taskInstanceQueryToTemp = new ArrayList<TaskInstance>();
		TaskInstance taskInstanceQuery = taskInstance;
		List<String> tokenIdList = new ArrayList<String>();
		TokenEntity token = ((TaskInstanceEntity) taskInstance).getToken();
		getTokenParent(token, tokenIdList);
		@SuppressWarnings({ "unchecked", "rawtypes" })
		List<TaskInstance> taskInstanceQueryToList = (List) Context.getCommandContext().getTaskManager().findTasksByTokenIdList(tokenIdList);

		for (TaskInstance taskInstanceQueryTo : taskInstanceQueryToList) {
			if (!taskInstanceQueryTo.getId().equals(taskInstanceQuery.getId())) {
				if (taskInstanceQueryTo.getTaskGroup() != null) {

					taskInstanceQueryToTemp.add(taskInstanceQueryTo);
				} else {
					if (taskInstanceQueryToTemp.size() == 0) {
						taskInstanceQueryToTemp.add(taskInstanceQueryTo);
						return taskInstanceQueryToTemp;
					} else {
						return taskInstanceQueryToTemp;
					}

				}
			}
		}
		
		if(taskInstanceQueryToTemp.size()==0){
			List<TaskInstanceEntity> taskInstanceEntitys=((TokenEntity)token).getProcessInstance().getTaskMgmtInstance().getTaskInstanceEntitys();
			
			if(taskInstanceEntitys.size()>0){
				taskInstanceQueryToTemp.add(taskInstanceEntitys.get(0));
			}
			
		}

		return taskInstanceQueryToTemp;
	}

	/**
	 * 获取当前令牌所有可以被退回的任务
	 * @param token
	 * @return
	 */
	public static List<TaskInstance> getRollBackTaskByToken(Token token) {

		List<TaskInstance> taskInstanceQueryToTemp = new ArrayList<TaskInstance>();

		List<String> tokenIdList = new ArrayList<String>();
		TokenEntity tokenObj = (TokenEntity) token;
		getTokenParent(tokenObj, tokenIdList);
		@SuppressWarnings({ "unchecked", "rawtypes" })
		List<TaskInstance> taskInstanceQueryToList = (List) Context.getCommandContext().getTaskManager().findTasksByTokenIdList(tokenIdList);

		for (TaskInstance taskInstanceQueryTo : taskInstanceQueryToList) {

			if (taskInstanceQueryTo.getTaskGroup() != null) {

				taskInstanceQueryToTemp.add(taskInstanceQueryTo);
			} else {
				if (taskInstanceQueryToTemp.size() == 0) {
					taskInstanceQueryToTemp.add(taskInstanceQueryTo);
					return taskInstanceQueryToTemp;
				} else {
					return taskInstanceQueryToTemp;
				}

			}

		}
		
		
		if(taskInstanceQueryToTemp.size()==0){
			List<TaskInstanceEntity> taskInstanceEntitys=((TokenEntity)token).getProcessInstance().getTaskMgmtInstance().getTaskInstanceEntitys();
			
			if(taskInstanceEntitys.size()>0){

				taskInstanceQueryToTemp.add(taskInstanceEntitys.get(0));
			}
			
		}

		return taskInstanceQueryToTemp;

	}

	/**
	 * 获取当前令牌的所有父节点
	 * @param token
	 * @param tokenList
	 */
	public static void getTokenParent(TokenEntity token, List<String> tokenList) {

		tokenList.add(token.getId());
		if (token.getParent() != null) {
			getTokenParent(token.getParent(), tokenList);
		}
	}

}
