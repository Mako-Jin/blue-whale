package com.bluewhale.workflow.service.impl.task;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.service.ProcessCcService;
import com.bluewhale.workflow.service.ProcessDelegateService;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
import com.bluewhale.workflow.support.machine.TransitionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TaskActivationCommand} 待办创建 / 审批人覆盖 / 代理改派 / 抄送测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TaskActivationCommandTest {

    /** 参数错误码 */
    private static final int PARAM_ERROR_CODE = 400;

    /** 含抄送节点与用户任务的流程定义 */
    private static final String MODEL_JSON = "{\"nodes\":["
            + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
            + "{\"key\":\"cc1\",\"type\":\"CC_TASK\",\"name\":\"抄送财务\",\"assignees\":[\"fin01\",\"fin02\"]},"
            + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"name\":\"部门审批\",\"assignee\":\"manager\"},"
            + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
            + "\"flows\":["
            + "{\"key\":\"f1\",\"from\":\"start\",\"to\":\"cc1\"},"
            + "{\"key\":\"f2\",\"from\":\"cc1\",\"to\":\"t1\"},"
            + "{\"key\":\"f3\",\"from\":\"t1\",\"to\":\"end\"}]}";

    @Mock
    private ProcessTaskMapper processTaskMapper;

    @Mock
    private ProcessDelegateService processDelegateService;

    @Mock
    private ProcessCcService processCcService;

    @InjectMocks
    private TaskActivationCommand taskActivationCommand;

    @Test
    @DisplayName("activate：静态审批人建 1 条待办，抄送节点只写抄送")
    void shouldCreateOneTaskAndWriteCc() {
        ProcessInstancePo instance = instance(null);
        TransitionResult result = result();

        taskActivationCommand.activate(instance, model(), result);

        ArgumentCaptor<ProcessTaskPo> captor = ArgumentCaptor.forClass(ProcessTaskPo.class);
        verify(processTaskMapper).insert(captor.capture());
        assertEquals("manager", captor.getValue().getAssignee());
        assertEquals("manager", captor.getValue().getOwnerId());
        assertNull(captor.getValue().getDelegateFrom());
        verify(processCcService).createNodeCc(eq(1L), eq("cc1"), eq("抄送财务"),
                eq(List.of("fin01", "fin02")));
    }

    @Test
    @DisplayName("activate：审批人覆盖生成多条待办")
    void shouldCreateTasksForOverrides() {
        ProcessInstancePo instance = instance("{\"t1\":[\"lisi\",\"lisi2\"]}");

        taskActivationCommand.activate(instance, model(), result());

        verify(processTaskMapper, times(2)).insert(any(ProcessTaskPo.class));
    }

    @Test
    @DisplayName("activate：代理命中时改派并记录 delegateFrom")
    void shouldDelegateTaskOnce() {
        ProcessInstancePo instance = instance(null);
        when(processDelegateService.resolveActive(eq("wf"), eq("manager"), any()))
                .thenReturn(Optional.of("zhaoliu"));

        taskActivationCommand.activate(instance, model(), result());

        ArgumentCaptor<ProcessTaskPo> captor = ArgumentCaptor.forClass(ProcessTaskPo.class);
        verify(processTaskMapper).insert(captor.capture());
        assertEquals("zhaoliu", captor.getValue().getAssignee());
        assertEquals("manager", captor.getValue().getOwnerId());
        assertEquals("manager", captor.getValue().getDelegateFrom());
        verify(processDelegateService, times(1)).resolveActive(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("activate：节点无审批人时抛 PARAM_ERROR 且不建待办")
    void shouldRejectNodeWithoutAssignee() {
        ProcessModel model = model();
        ProcessModelNode node = model.node("t1");
        node.setAssignee(null);

        BizException ex = assertThrows(BizException.class,
                () -> taskActivationCommand.activate(instance(null), model,
                        TransitionResult.at(List.of(node))));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("t1"));
        verify(processTaskMapper, never()).insert(any(ProcessTaskPo.class));
    }

    /**
     * 构造测试实例。
     *
     * @param overridesJson 审批人覆盖 JSON
     * @return 流程实例
     */
    private static ProcessInstancePo instance(String overridesJson) {
        ProcessInstancePo instance = new ProcessInstancePo();
        instance.setId(1L);
        instance.setDefinitionCode("wf");
        instance.setAssigneeOverridesJson(overridesJson);
        return instance;
    }

    /**
     * 解析测试模型。
     *
     * @return 流程模型
     */
    private static ProcessModel model() {
        return ProcessModelParser.parse(MODEL_JSON);
    }

    /**
     * 构造抵达 t1 且途经 cc1 的迁移结果。
     *
     * @return 迁移结果
     */
    private static TransitionResult result() {
        ProcessModel model = model();
        return TransitionResult.at(List.of(model.node("t1")), List.of(model.node("cc1")));
    }
}
