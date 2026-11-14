package com.bluewhale.workflow.service.impl.task;

import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.support.WorkflowConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApproveCommand} 发起人重新提交分支单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApproveCommandTest {

    /** 线性流程：start → n1 → n2 → end */
    private static final String MODEL = "{\"nodes\":["
            + "{\"key\":\"start\",\"type\":\"START_EVENT\",\"name\":\"发起\"},"
            + "{\"key\":\"n1\",\"type\":\"USER_TASK\",\"name\":\"部门审批\",\"assignee\":\"manager\"},"
            + "{\"key\":\"n2\",\"type\":\"USER_TASK\",\"name\":\"总监审批\",\"assignee\":\"director\"},"
            + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],\"flows\":["
            + "{\"key\":\"f1\",\"from\":\"start\",\"to\":\"n1\"},"
            + "{\"key\":\"f2\",\"from\":\"n1\",\"to\":\"n2\"},"
            + "{\"key\":\"f3\",\"from\":\"n2\",\"to\":\"end\"}]}";

    @Mock
    private ProcessInstanceMapper processInstanceMapper;

    @Mock
    private ProcessTaskMapper processTaskMapper;

    @Mock
    private TaskCommandSupport taskCommandSupport;

    @Mock
    private InstanceModelLoader instanceModelLoader;

    @Mock
    private CounterSignCommand counterSignCommand;

    @Mock
    private TaskActivationCommand taskActivationCommand;

    private ApproveCommand approveCommand;

    @BeforeEach
    void setUp() {
        approveCommand = new ApproveCommand(processInstanceMapper, taskCommandSupport,
                instanceModelLoader, counterSignCommand, taskActivationCommand);
    }

    @Test
    @DisplayName("重新提交待办同意：从入口推进到首个审批节点并建待办")
    void shouldAdvanceFromEntryWhenResubmitApproved() {
        ProcessTaskPo resubmit = new ProcessTaskPo();
        resubmit.setId(100L);
        resubmit.setInstanceId(10L);
        resubmit.setNodeKey(WorkflowConstants.RESUBMIT_NODE_KEY);
        resubmit.setStatus(WorkflowConstants.TASK_PENDING);
        ProcessInstancePo instance = new ProcessInstancePo();
        instance.setId(10L);
        instance.setStatus(WorkflowConstants.INSTANCE_RUNNING);
        instance.setDefinitionCode("demo");
        instance.setCurrentNodeKey("start");
        when(taskCommandSupport.requirePendingTask(100L)).thenReturn(resubmit);
        when(taskCommandSupport.requireRunningInstance(10L)).thenReturn(instance);
        when(taskCommandSupport.transitionFromPending(any(), anyInt(), any())).thenReturn(true);
        when(instanceModelLoader.resolveInstanceJson(instance)).thenReturn(MODEL);
        when(processInstanceMapper.updateById(any(ProcessInstancePo.class))).thenReturn(1);

        approveCommand.approve(100L, null);

        assertEquals("n1", instance.getCurrentNodeKey());
        assertEquals(1, instance.getCurrentIndex());
        verify(taskActivationCommand).activate(eq(instance), any(), any());
        verify(counterSignCommand, org.mockito.Mockito.never()).awaitSameNode(any(), any(), any());
    }
}
