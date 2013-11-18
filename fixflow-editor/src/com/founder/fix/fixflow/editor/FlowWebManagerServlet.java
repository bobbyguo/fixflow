package com.founder.fix.fixflow.editor;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.codehaus.jackson.node.ObjectNode;

import com.founder.fix.fixflow.bpmn.converter.FixFlowConverter;
import com.founder.fix.fixflow.explorer.BaseServlet;
import com.founder.fix.fixflow.service.FlowCenterService;


/**
 * 
 * 职责：web流程图的基本操作
 * 开发者：徐海洋
 */
public class FlowWebManagerServlet extends BaseServlet {
	private static final long serialVersionUID = 1L;
       
   
	/**
	 * 任务：加载web流程图，返回json格式对象
	 */
    public void loadBPMWeb(){
    	try {
            InputStream input = new FileInputStream(new File(session(FlowCenterService.LOGIN_USER_ID)+File.separator+request("path")+File.separator+request("fileName"))); 
    		ObjectNode on = new FixFlowConverter().convertBpmn2Json("process_testych", input);
    		ajaxResultObj(on);
		} catch (Exception e) {
			error("加载web流程图，返回json格式对象出错!");
		}
    }
}