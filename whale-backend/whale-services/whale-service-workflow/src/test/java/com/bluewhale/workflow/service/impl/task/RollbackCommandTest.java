package com.bluewhale.workflow.service.impl.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.support.WorkflowConstants;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RollbackCommand} 回退目标校验单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RollbackCommandTest {

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
    private TaskActivationCommand taskActivationCommand;

    @Mock
    private InstanceModelLoader instanceModelLoader;

    private RollbackCommand rollbackCommand;

    private ProcessInstancePo instance;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProcessTaskPo.class);
        TableInfoHelper.initTableInfo(assistant, ProcessInstancePo.class);
    }

    @BeforeEach
    void setUp() {
        rollbackCommand = new RollbackCommand(processInstanceMapper,
                new TaskCommandSupport(processTaskMapper, processInstanceMapper),
                taskActivationCommand, instanceModelLoader);
        instance = new ProcessInstancePo();
        instance.setId(10L);
        instance.setStatus(WorkflowConstants.INSTANCE_RUNNING);
        instance.setDefinitionCode("demo");
        instance.setCurrentNodeKey("n2");
        instance.setCurrentIndex(2);
        when(processTaskMapper.selectById(100L)).thenReturn(task(100L, "n2"));
        when(processInstanceMapper.selectById(10L)).thenReturn(instance);
        when(instanceModelLoader.resolveInstanceJson(instance)).thenReturn(MODEL);
        when(processTaskMapper.update(any(ProcessTaskPo.class), any())).thenReturn(1);
    }

    @Test
    @DisplayName("回退到上游审批节点：取消待办并在目标节点重建")
    void shouldRollbackToUpstreamNode() {
        rollbackCommand.rollback(100L, "n1");

        assertEquals("n1", instance.getCurrentNodeKey());
        verify(taskActivationCommand).activate(eq(instance), any(), any());
    }

    @Test
    @DisplayName("回退到当前节点被拒绝")
    void shouldRejectCurrentNode() {
        BizException error = assertThrows(BizException.class,
                () -> rollbackCommand.rollback(100L, "n2"));

        assertTrue(error.getMessage().contains("当前节点"));
        verify(taskActivationCommand, never()).activate(any(), any(), any());
    }

    @Test
    @DisplayName("回退到下游节点被拒绝")
    void shouldRejectDownstreamNode() {
        instance.setCurrentNodeKey("n1");
        instance.setCurrentIndex(1);
        when(processTaskMapper.selectById(100L)).thenReturn(task(100L, "n1"));

        BizException error = assertThrows(BizException.class,
                () -> rollbackCommand.rollback(100L, "n2"));

        assertTrue(error.getMessage().contains("上游"));
        verify(taskActivationCommand, never()).activate(any(), any(), any());
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
}
