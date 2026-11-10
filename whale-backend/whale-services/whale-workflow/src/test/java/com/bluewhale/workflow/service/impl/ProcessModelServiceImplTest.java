package com.bluewhale.workflow.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.workflow.dto.BpmnImportRequest;
import com.bluewhale.workflow.dto.DefinitionSimulateRequest;
import com.bluewhale.workflow.dto.DefinitionValidateRequest;
import com.bluewhale.workflow.dto.ProcessDefinitionSaveRequest;
import com.bluewhale.workflow.service.ProcessDefinitionService;
import com.bluewhale.workflow.vo.ProcessDefinitionSimulateVO;
import com.bluewhale.workflow.vo.ProcessDefinitionVO;
import com.bluewhale.workflow.vo.ProcessDefinitionValidateVO;
import com.bluewhale.workflow.vo.SimulateStepVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProcessModelServiceImpl} 定义校验 / BPMN 导入 / 导出单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ProcessModelServiceImplTest {

    /** 规范化合法定义 */
    private static final String NORMALIZED_JSON =
            "{\"type\":\"stateMachine\",\"nodes\":["
                    + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                    + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"name\":\"审批\",\"assignee\":\"admin\"},"
                    + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                    + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"},"
                    + "{\"key\":\"f2\",\"from\":\"t1\",\"to\":\"end\"}]}";

    /** legacy 合法定义 */
    private static final String LEGACY_JSON =
            "{\"nodes\":[{\"key\":\"n1\",\"assignee\":\"admin\"},{\"key\":\"n2\",\"assignee\":\"user\"}]}";

    /** 基础 BPMN XML */
    private static final String BASIC_BPMN = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" targetNamespace=\"http://bw\">"
            + "<process id=\"p1\"><startEvent id=\"start\"/><userTask id=\"t1\" assignee=\"admin\"/>"
            + "<endEvent id=\"end\"/><sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"t1\"/>"
            + "<sequenceFlow id=\"f2\" sourceRef=\"t1\" targetRef=\"end\"/></process></definitions>";

    /** 含不支持元素的 BPMN XML */
    private static final String UNSUPPORTED_BPMN = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
            + "<process id=\"p1\"><startEvent id=\"start\"/><serviceTask id=\"s1\"/></process></definitions>";

    /** 含表单绑定与画布坐标的规范化定义 */
    private static final String NORMALIZED_FORM_JSON =
            "{\"type\":\"stateMachine\",\"nodes\":["
                    + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                    + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"name\":\"审批\",\"assignee\":\"admin\","
                    + "\"formId\":\"2112355156699869186\",\"formCode\":\"bw_d29_form\",\"x\":320,\"y\":180},"
                    + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                    + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"},"
                    + "{\"key\":\"f2\",\"from\":\"t1\",\"to\":\"end\"}]}";

    /** 双分支定义：金额大于 100 走 manager，否则走默认 clerk */
    private static final String DUAL_BRANCH_JSON =
            "{\"type\":\"stateMachine\",\"nodes\":["
                    + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                    + "{\"key\":\"gw\",\"type\":\"EXCLUSIVE_GATEWAY\"},"
                    + "{\"key\":\"high\",\"type\":\"USER_TASK\",\"name\":\"高额审批\",\"assignee\":\"manager\"},"
                    + "{\"key\":\"low\",\"type\":\"USER_TASK\",\"name\":\"低额审批\",\"assignee\":\"clerk\"},"
                    + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                    + "\"flows\":[{\"key\":\"f0\",\"from\":\"start\",\"to\":\"gw\"},"
                    + "{\"key\":\"fHigh\",\"from\":\"gw\",\"to\":\"high\",\"condition\":\"amount > 100\"},"
                    + "{\"key\":\"fLow\",\"from\":\"gw\",\"to\":\"low\",\"isDefault\":true},"
                    + "{\"key\":\"fh\",\"from\":\"high\",\"to\":\"end\"},"
                    + "{\"key\":\"fl\",\"from\":\"low\",\"to\":\"end\"}]}";

    /** 多条件无默认流定义（运行期可能无匹配分支） */
    private static final String NO_DEFAULT_BRANCH_JSON =
            "{\"type\":\"stateMachine\",\"nodes\":["
                    + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                    + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"name\":\"审批\",\"assignee\":\"admin\"},"
                    + "{\"key\":\"a\",\"type\":\"USER_TASK\",\"name\":\"A\",\"assignee\":\"alice\"},"
                    + "{\"key\":\"b\",\"type\":\"USER_TASK\",\"name\":\"B\",\"assignee\":\"bob\"},"
                    + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                    + "\"flows\":[{\"key\":\"f0\",\"from\":\"start\",\"to\":\"t1\"},"
                    + "{\"key\":\"fa\",\"from\":\"t1\",\"to\":\"a\",\"condition\":\"amount > 100\"},"
                    + "{\"key\":\"fb\",\"from\":\"t1\",\"to\":\"b\",\"condition\":\"level == 'VIP'\"},"
                    + "{\"key\":\"fa2\",\"from\":\"a\",\"to\":\"end\"},"
                    + "{\"key\":\"fb2\",\"from\":\"b\",\"to\":\"end\"}]}";

    @Mock
    private ProcessDefinitionService processDefinitionService;

    @InjectMocks
    private ProcessModelServiceImpl processModelService;

    /**
     * 构造 JSON 校验请求。
     *
     * @param definitionJson 定义 JSON
     * @return 校验请求
     */
    private static DefinitionValidateRequest jsonRequest(String definitionJson) {
        DefinitionValidateRequest request = new DefinitionValidateRequest();
        request.setDefinitionJson(definitionJson);
        return request;
    }

    @Test
    @DisplayName("validate：规范化 JSON 合法返回归一化结果与计数")
    void shouldValidateNormalizedJson() {
        ProcessDefinitionValidateVO vo = processModelService.validate(jsonRequest(NORMALIZED_JSON));

        assertTrue(vo.isValid());
        assertEquals("NORMALIZED", vo.getMode());
        assertEquals(3, vo.getNodeCount());
        assertEquals(2, vo.getFlowCount());
        assertNotNull(vo.getNormalizedJson());
        assertTrue(vo.getNormalizedJson().contains("stateMachine"));
    }

    @Test
    @DisplayName("validate：legacy JSON 合法且 mode=LEGACY")
    void shouldValidateLegacyJson() {
        ProcessDefinitionValidateVO vo = processModelService.validate(jsonRequest(LEGACY_JSON));

        assertTrue(vo.isValid());
        assertEquals("LEGACY", vo.getMode());
    }

    @Test
    @DisplayName("validate：非法 JSON 返回 valid=false 且带问题码")
    void shouldReportInvalidJson() {
        ProcessDefinitionValidateVO vo = processModelService.validate(jsonRequest("not-a-json"));

        assertFalse(vo.isValid());
        assertFalse(vo.getErrors().isEmpty());
        assertEquals("MISSING_NODES", vo.getErrors().get(0).getCode());
    }

    @Test
    @DisplayName("validate：definitionJson 与 bpmnXml 同时为空抛参数错误")
    void shouldRejectEmptyValidateRequest() {
        BizException ex = assertThrows(BizException.class,
                () -> processModelService.validate(new DefinitionValidateRequest()));

        assertEquals(400, ex.getCode());
        assertEquals("definitionJson 与 bpmnXml 不能同时为空", ex.getMessage());
    }

    @Test
    @DisplayName("validate：BPMN XML 合法且 mode=BPMN")
    void shouldValidateBpmn() {
        DefinitionValidateRequest request = new DefinitionValidateRequest();
        request.setBpmnXml(BASIC_BPMN);

        ProcessDefinitionValidateVO vo = processModelService.validate(request);

        assertTrue(vo.isValid());
        assertEquals("BPMN", vo.getMode());
        assertEquals(3, vo.getNodeCount());
        assertTrue(vo.getNormalizedJson().contains("stateMachine"));
    }

    @Test
    @DisplayName("validate：BPMN 含不支持元素返回 valid=false")
    void shouldReportUnsupportedBpmn() {
        DefinitionValidateRequest request = new DefinitionValidateRequest();
        request.setBpmnXml(UNSUPPORTED_BPMN);

        ProcessDefinitionValidateVO vo = processModelService.validate(request);

        assertFalse(vo.isValid());
        assertEquals("BPMN_UNSUPPORTED_ELEMENT", vo.getErrors().get(0).getCode());
    }

    @Test
    @DisplayName("importBpmn：合法 BPMN 委托建定义并写入规范化 JSON")
    void shouldImportBpmn() {
        ProcessDefinitionVO created = new ProcessDefinitionVO();
        created.setId(8L);
        created.setCode("wf-bpmn");
        when(processDefinitionService.create(any(ProcessDefinitionSaveRequest.class))).thenReturn(created);
        BpmnImportRequest request = new BpmnImportRequest();
        request.setCode("wf-bpmn");
        request.setName("BPMN流程");
        request.setBpmnXml(BASIC_BPMN);

        ProcessDefinitionVO vo = processModelService.importBpmn(request);

        ArgumentCaptor<ProcessDefinitionSaveRequest> captor = ArgumentCaptor.forClass(ProcessDefinitionSaveRequest.class);
        verify(processDefinitionService).create(captor.capture());
        assertEquals("wf-bpmn", captor.getValue().getCode());
        assertTrue(captor.getValue().getDefinitionJson().contains("stateMachine"));
        assertEquals(8L, vo.getId());
    }

    @Test
    @DisplayName("importBpmn：非法 BPMN 抛参数错误且不建定义")
    void shouldRejectInvalidImport() {
        BpmnImportRequest request = new BpmnImportRequest();
        request.setCode("wf-bpmn");
        request.setName("BPMN流程");
        request.setBpmnXml(UNSUPPORTED_BPMN);

        BizException ex = assertThrows(BizException.class, () -> processModelService.importBpmn(request));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("不支持的 BPMN 元素"));
        verify(processDefinitionService, never()).create(any(ProcessDefinitionSaveRequest.class));
    }

    @Test
    @DisplayName("exportBpmn：定义转 BPMN XML 字节")
    void shouldExportBpmn() {
        ProcessDefinitionVO definition = new ProcessDefinitionVO();
        definition.setId(1L);
        definition.setCode("wf-a");
        definition.setName("A流程");
        definition.setDefinitionJson(NORMALIZED_JSON);
        when(processDefinitionService.getDetail(1L)).thenReturn(definition);

        byte[] content = processModelService.exportBpmn(1L);

        assertTrue(content.length > 0);
        String xml = new String(content, StandardCharsets.UTF_8);
        assertTrue(xml.contains("<definitions"));
        assertTrue(xml.contains("<process id=\"wf-a\""));
    }

    @Test
    @DisplayName("validate：formId/formCode/x/y 在归一化 JSON 中往返保留")
    void shouldKeepFormAndCoordinatesInNormalizedJson() {
        ProcessDefinitionValidateVO vo = processModelService.validate(jsonRequest(NORMALIZED_FORM_JSON));

        assertTrue(vo.isValid());
        String json = vo.getNormalizedJson();
        assertTrue(json.contains("\"formId\":\"2112355156699869186\""));
        assertTrue(json.contains("\"formCode\":\"bw_d29_form\""));
        assertTrue(json.contains("\"x\":320"));
        assertTrue(json.contains("\"y\":180"));
    }

    @Test
    @DisplayName("exportBpmn：表单绑定节点导出含 plain formKey/formId")
    void shouldExportBpmnWithFormKey() {
        ProcessDefinitionVO definition = new ProcessDefinitionVO();
        definition.setId(2L);
        definition.setCode("wf-form");
        definition.setName("表单流程");
        definition.setDefinitionJson(NORMALIZED_FORM_JSON);
        when(processDefinitionService.getDetail(2L)).thenReturn(definition);

        String xml = new String(processModelService.exportBpmn(2L), StandardCharsets.UTF_8);

        assertTrue(xml.contains("formKey=\"bw_d29_form\""));
        assertTrue(xml.contains("formId=\"2112355156699869186\""));
    }

    /**
     * 构造模拟请求。
     *
     * @param definitionJson 定义 JSON
     * @param variables 流程变量，可为 {@code null}
     * @return 模拟请求
     */
    private static DefinitionSimulateRequest simulateRequest(String definitionJson, Map<String, Object> variables) {
        DefinitionSimulateRequest request = new DefinitionSimulateRequest();
        request.setDefinitionJson(definitionJson);
        request.setVariables(variables);
        return request;
    }

    /**
     * 提取模拟路径的节点键序列。
     *
     * @param vo 模拟结果
     * @return 节点键列表
     */
    private static List<String> keysOf(ProcessDefinitionSimulateVO vo) {
        return vo.getSteps().stream().map(SimulateStepVO::getNodeKey).toList();
    }

    @Test
    @DisplayName("simulate：条件命中走 high 分支并抵达结束事件")
    void shouldSimulateConditionalBranch() {
        ProcessDefinitionSimulateVO vo = processModelService.simulate(
                simulateRequest(DUAL_BRANCH_JSON, Map.of("amount", 200)));

        assertTrue(vo.isValid());
        assertEquals("NORMALIZED", vo.getMode());
        assertTrue(vo.isReachedEnd());
        assertEquals("end", vo.getTerminalNodeKey());
        assertNull(vo.getErrorCode());
        assertEquals(List.of("start", "gw", "high", "end"), keysOf(vo));
        SimulateStepVO branch = vo.getSteps().get(2);
        assertEquals("fHigh", branch.getFlowKey());
        assertEquals("amount > 100", branch.getCondition());
        assertFalse(branch.getIsDefault());
        assertEquals("manager", branch.getAssignee());
    }

    @Test
    @DisplayName("simulate：条件未命中走默认流 low 分支")
    void shouldSimulateDefaultBranch() {
        ProcessDefinitionSimulateVO vo = processModelService.simulate(
                simulateRequest(DUAL_BRANCH_JSON, Map.of("amount", 10)));

        assertTrue(vo.isReachedEnd());
        assertEquals(List.of("start", "gw", "low", "end"), keysOf(vo));
        SimulateStepVO branch = vo.getSteps().get(2);
        assertEquals("fLow", branch.getFlowKey());
        assertTrue(branch.getIsDefault());
        assertEquals("clerk", branch.getAssignee());
        assertEquals("USER_TASK", branch.getNodeType());
    }

    @Test
    @DisplayName("simulate：条件均未命中且无默认流返回 NO_MATCHING_FLOW")
    void shouldReportNoMatchingFlow() {
        ProcessDefinitionSimulateVO vo = processModelService.simulate(
                simulateRequest(NO_DEFAULT_BRANCH_JSON, Map.of("amount", 10, "level", "NORMAL")));

        assertTrue(vo.isValid());
        assertEquals("NO_MATCHING_FLOW", vo.getErrorCode());
        assertNotNull(vo.getErrorMessage());
        assertFalse(vo.isReachedEnd());
        assertEquals("t1", vo.getTerminalNodeKey());
        assertEquals(List.of("start", "t1"), keysOf(vo));
    }

    @Test
    @DisplayName("simulate：非法 JSON 返回 valid=false 且无路径")
    void shouldRejectInvalidDefinitionOnSimulate() {
        ProcessDefinitionSimulateVO vo = processModelService.simulate(simulateRequest("not-a-json", null));

        assertFalse(vo.isValid());
        assertFalse(vo.getErrors().isEmpty());
        assertEquals("MISSING_NODES", vo.getErrors().get(0).getCode());
        assertTrue(vo.getSteps().isEmpty());
    }

    @Test
    @DisplayName("simulate：legacy 定义按顺序走完节点")
    void shouldSimulateLegacyDefinition() {
        ProcessDefinitionSimulateVO vo = processModelService.simulate(simulateRequest(LEGACY_JSON, null));

        assertTrue(vo.isValid());
        assertEquals("LEGACY", vo.getMode());
        assertEquals(List.of("n1", "n2"), keysOf(vo));
        assertEquals("n2", vo.getTerminalNodeKey());
        assertFalse(vo.isReachedEnd());
    }
}
