package com.bluewhale.workflow.service;

import com.bluewhale.workflow.request.BpmnImportRequest;
import com.bluewhale.workflow.request.DefinitionSimulateRequest;
import com.bluewhale.workflow.request.DefinitionValidateRequest;
import com.bluewhale.workflow.vo.ProcessDefinitionSimulateVO;
import com.bluewhale.workflow.vo.ProcessDefinitionValidateVO;
import com.bluewhale.workflow.vo.ProcessDefinitionVO;

/**
 * 流程模型服务：定义校验、BPMN 导入、BPMN 导出。
 *
 * @author BlueWhale
 */
public interface ProcessModelService {

    /**
     * 校验流程定义（JSON 或 BPMN XML），内容非法不抛异常。
     *
     * @param request 校验请求：{@code definitionJson} 与 {@code bpmnXml} 二选一
     * @return 校验视图对象
     */
    ProcessDefinitionValidateVO validate(DefinitionValidateRequest request);

    /**
     * 按真实条件求值模拟流程走向（不创建实例、不落库）。
     *
     * @param request 模拟请求：{@code definitionJson} 与流程变量
     * @return 模拟结果（结构错误 / 运行期选路错误 / 节点路径）
     */
    ProcessDefinitionSimulateVO simulate(DefinitionSimulateRequest request);

    /**
     * 导入 BPMN XML 建流程定义（XML → 规范化 JSON 落库）。
     *
     * @param request 导入请求：code / name / bpmnXml 必填
     * @return 流程定义视图对象
     */
    ProcessDefinitionVO importBpmn(BpmnImportRequest request);

    /**
     * 导出流程定义为 BPMN XML 字节。
     *
     * @param id 流程定义ID
     * @return BPMN XML 字节（UTF-8）
     */
    byte[] exportBpmn(Long id);
}
