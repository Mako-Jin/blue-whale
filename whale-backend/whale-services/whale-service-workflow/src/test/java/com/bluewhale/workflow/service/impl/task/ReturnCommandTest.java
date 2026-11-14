package com.bluewhale.workflow.service.impl.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.request.TaskRejectRequest;
import com.bluewhale.workflow.support.RejectTarget;
import com.bluewhale.workflow.support.WorkflowConstants;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReturnCommand} 驳回退回单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReturnCommandTest {

    /** 线性流程：start → n1 → n2 → end */
    private static final String MODEL = "{\"nodes\":["
            + "{\"key\":\"start\",\"type\":\"START_EVENT\",\"name\":\"发起\"},"
            + "{\"key\":\"n1\",\"type\":\"USER_TASK\",\"name\":\"部门审批\",\"assignee\":\"manager\"},"
            + "{\"key\":\"n2\",\"type\":\"USER_TASK\",\"name\":\"总监审批\",\"assignee\":\"director\"},"
            + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],\"flows\":["
            + "{\"key\":\"f1\",\"from\":\"start\",\"to\":\"n1\"},"
            + "{\"key\":\"f2\",\"from\":\"n1\",\"to\":\"n2\"},"
            + "{\"key\":\"f3\",\"from\":\"n2\",\"to\":\"end\"}]}";

    /** 会签流程：start → n1 → c1 → end */
    private static final String COUNTER_MODEL = "{\"nodes\":["
            + "{\"key\":\"start\",\"type\":\"START_EVENT\",\"name\":\"发起\"},"
            + "{\"key\":\"n1\",\"type\":\"USER_TASK\",\"name\":\"部门审批\",\"assignee\":\"manager\"},"
            + "{\"key\":\"c1\",\"type\":\"COUNTER_SIGN\",\"name\":\"会签\","
            + "\"assignees\":[\"a\",\"b\"],\"counterSignPolicy\":\"ALL\"},"
            + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],\"flows\":["
            + "{\"key\":\"f1\",\"from\":\"start\",\"to\":\"n1\"},"
            + "{\"key\":\"f2\",\"from\":\"n1\",\"to\":\"c1\"},"
            + "{\"key\":\"f3\",\"from\":\"c1\",\"to\":\"end\"}]}";

    /** 首节点即会签：start → c1 → end */
    private static final String COUNTER_FIRST_MODEL = "{\"nodes\":["
            + "{\"key\":\"start\",\"type\":\"START_EVENT\",\"name\":\"发起\"},"
            + "{\"key\":\"c1\",\"type\":\"COUNTER_SIGN\",\"name\":\"会签\","
            + "\"assignees\":[\"a\",\"b\"],\"counterSignPolicy\":\"ALL\"},"
            + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],\"flows\":["
            + "{\"key\":\"f1\",\"from\":\"start\",\"to\":\"c1\"},"
            + "{\"key\":\"f2\",\"from\":\"c1\",\"to\":\"end\"}]}";

    @Mock
    private ProcessInstanceMapper processInstanceMapper;

    @Mock
    private ProcessTaskMapper processTaskMapper;

    @Mock
    private InstanceModelLoader instanceModelLoader;

    @Mock
    private TaskActivationCommand taskActivationCommand;

    @Mock
    private CounterSignCommand counterSignCommand;

    private ReturnCommand returnCommand;

    private ProcessInstancePo instance;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProcessTaskPo.class);
        TableInfoHelper.initTableInfo(assistant, ProcessInstancePo.class);
    }

    @BeforeEach
    void setUp() {
        returnCommand = new ReturnCommand(processInstanceMapper, processTaskMapper,
                new TaskCommandSupport(processTaskMapper, processInstanceMapper),
                instanceModelLoader, taskActivationCommand, counterSignCommand);
        instance = new ProcessInstancePo();
        instance.setId(10L);
        instance.setStatus(WorkflowConstants.INSTANCE_RUNNING);
        instance.setInitiator("alice");
        instance.setDefinitionCode("demo");
        instance.setCurrentNodeKey("n2");
        instance.setCurrentIndex(2);
        when(processInstanceMapper.selectById(10L)).thenReturn(instance);
        when(processInstanceMapper.updateById(any(ProcessInstancePo.class))).thenReturn(1);
        when(processTaskMapper.update(any(ProcessTaskPo.class), any())).thenReturn(1);
    }

    @Test
    @DisplayName("驳回到发起人：取消待办、实例停在入口、生成重新提交待办")
    void shouldReturnToInitiator() {
        when(processTaskMapper.selectById(100L)).thenReturn(task(100L, "n2"));
        when(instanceModelLoader.resolveInstanceJson(instance)).thenReturn(MODEL);

        returnCommand.returnTask(100L, request(RejectTarget.INITIATOR, null));

        ArgumentCaptor<ProcessTaskPo> captor = ArgumentCaptor.forClass(ProcessTaskPo.class);
        verify(processTaskMapper).insert(captor.capture());
        ProcessTaskPo resubmit = captor.getValue();
        assertEquals(WorkflowConstants.RESUBMIT_NODE_KEY, resubmit.getNodeKey());
        assertEquals("alice", resubmit.getAssignee());
        assertEquals(WorkflowConstants.TASK_PENDING, resubmit.getStatus());
        assertEquals("start", instance.getCurrentNodeKey());
        verify(taskActivationCommand, never()).activate(any(), any(), any());
    }

    @Test
    @DisplayName("驳回到上一节点：在唯一上游审批节点重建待办")
    void shouldReturnToPreviousNode() {
        when(processTaskMapper.selectById(100L)).thenReturn(task(100L, "n2"));
        when(instanceModelLoader.resolveInstanceJson(instance)).thenReturn(MODEL);

        returnCommand.returnTask(100L, request(RejectTarget.PREVIOUS, null));

        assertEquals("n1", instance.getCurrentNodeKey());
        verify(taskActivationCommand).activate(eq(instance), any(), any());
    }

    @Test
    @DisplayName("驳回到指定节点：仅允许上游审批节点")
    void shouldReturnToSpecifiedAncestor() {
        when(processTaskMapper.selectById(100L)).thenReturn(task(100L, "n2"));
        when(instanceModelLoader.resolveInstanceJson(instance)).thenReturn(MODEL);

        returnCommand.returnTask(100L, request(RejectTarget.SPECIFIED, "n1"));

        assertEquals("n1", instance.getCurrentNodeKey());
        verify(taskActivationCommand).activate(eq(instance), any(), any());
    }

    @Test
    @DisplayName("驳回到下游/当前节点被拒绝")
    void shouldRejectNonAncestorTarget() {
        ProcessInstancePo first = instanceAt("n1", 1);
        when(processTaskMapper.selectById(100L)).thenReturn(task(100L, "n1"));
        when(instanceModelLoader.resolveInstanceJson(first)).thenReturn(MODEL);

        BizException forward = assertThrows(BizException.class,
                () -> returnCommand.returnTask(100L, request(RejectTarget.SPECIFIED, "n2")));
        BizException self = assertThrows(BizException.class,
                () -> returnCommand.returnTask(100L, request(RejectTarget.SPECIFIED, "n1")));

        assertTrue(forward.getMessage().contains("上游"));
        assertTrue(self.getMessage().contains("当前节点"));
    }

    @Test
    @DisplayName("首节点：允许退回发起人，不允许上一节点")
    void shouldAllowInitiatorOnlyOnFirstNode() {
        ProcessInstancePo first = instanceAt("n1", 1);
        when(processTaskMapper.selectById(100L)).thenReturn(task(100L, "n1"));
        when(instanceModelLoader.resolveInstanceJson(first)).thenReturn(MODEL);

        BizException error = assertThrows(BizException.class,
                () -> returnCommand.returnTask(100L, request(RejectTarget.PREVIOUS, null)));
        assertTrue(error.getMessage().contains("首节点"));
    }

    @Test
    @DisplayName("重新提交待办不允许驳回")
    void shouldRejectResubmitTask() {
        ProcessTaskPo resubmit = task(100L, WorkflowConstants.RESUBMIT_NODE_KEY);
        when(processTaskMapper.selectById(100L)).thenReturn(resubmit);

        BizException error = assertThrows(BizException.class,
                () -> returnCommand.returnTask(100L, request(RejectTarget.INITIATOR, null)));

        assertTrue(error.getMessage().contains("发起节点"));
    }

    @Test
    @DisplayName("会签 ALL：一人驳回立即退回上一节点")
    void shouldReturnWhenCounterSignAllRejected() {
        ProcessInstancePo counter = instanceAt("c1", 2);
        when(processTaskMapper.selectById(100L)).thenReturn(task(100L, "c1"));
        when(instanceModelLoader.resolveInstanceJson(counter)).thenReturn(COUNTER_MODEL);
        when(counterSignCommand.shouldReturnOnReject(anyLong(), eq("c1"), any())).thenReturn(true);

        returnCommand.returnTask(100L, request(RejectTarget.INITIATOR, null));

        assertEquals("n1", counter.getCurrentNodeKey());
        verify(taskActivationCommand).activate(eq(counter), any(), any());
    }

    @Test
    @DisplayName("会签等待：策略判定不退回时只落驳回痕迹")
    void shouldWaitWhenCounterSignNotFailed() {
        ProcessInstancePo counter = instanceAt("c1", 2);
        when(processTaskMapper.selectById(100L)).thenReturn(task(100L, "c1"));
        when(instanceModelLoader.resolveInstanceJson(counter)).thenReturn(COUNTER_MODEL);
        when(counterSignCommand.shouldReturnOnReject(anyLong(), eq("c1"), any())).thenReturn(false);

        returnCommand.returnTask(100L, request(RejectTarget.INITIATOR, null));

        assertEquals("c1", counter.getCurrentNodeKey());
        verify(taskActivationCommand, never()).activate(any(), any(), any());
        verify(processInstanceMapper, never()).updateById(any(ProcessInstancePo.class));
    }

    @Test
    @DisplayName("会签无上一层：退回发起人")
    void shouldReturnToInitiatorWhenCounterSignHasNoPrevious() {
        ProcessInstancePo counter = instanceAt("c1", 1);
        when(processTaskMapper.selectById(100L)).thenReturn(task(100L, "c1"));
        when(instanceModelLoader.resolveInstanceJson(counter)).thenReturn(COUNTER_FIRST_MODEL);
        when(counterSignCommand.shouldReturnOnReject(anyLong(), eq("c1"), any())).thenReturn(true);

        returnCommand.returnTask(100L, request(RejectTarget.SPECIFIED, "start"));

        ArgumentCaptor<ProcessTaskPo> captor = ArgumentCaptor.forClass(ProcessTaskPo.class);
        verify(processTaskMapper).insert(captor.capture());
        assertEquals(WorkflowConstants.RESUBMIT_NODE_KEY, captor.getValue().getNodeKey());
        assertEquals("start", counter.getCurrentNodeKey());
    }

    /**
     * 构造待办任务。
     *
     * @param id 任务ID
     * @param nodeKey 节点键
     * @return 待办任务
     */
    private static ProcessTaskPo task(Long id, String nodeKey) {
        ProcessTaskPo task = new ProcessTaskPo();
        task.setId(id);
        task.setInstanceId(10L);
        task.setNodeKey(nodeKey);
        task.setStatus(WorkflowConstants.TASK_PENDING);
        return task;
    }

    /**
     * 构造驳回请求。
     *
     * @param target 目标
     * @param targetNodeKey 指定节点键
     * @return 请求
     */
    private static TaskRejectRequest request(RejectTarget target, String targetNodeKey) {
        TaskRejectRequest request = new TaskRejectRequest();
        request.setTarget(target);
        request.setTargetNodeKey(targetNodeKey);
        request.setComment("不同意");
        return request;
    }

    /**
     * 把实例移动到指定节点。
     *
     * @param nodeKey 节点键
     * @param index 节点下标
     * @return 实例
     */
    private ProcessInstancePo instanceAt(String nodeKey, int index) {
        instance.setCurrentNodeKey(nodeKey);
        instance.setCurrentIndex(index);
        return instance;
    }
}
