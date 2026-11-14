package com.bluewhale.workflow.service.impl.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.support.WorkflowConstants;
import com.bluewhale.workflow.vo.TaskRejectOptionsVO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link TaskRejectOptionsCommand} 驳回选项单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskRejectOptionsCommandTest {

    /** 线性流程 + 会签：start → n1 → n2 → n3 及 start → n1 → c1 */
    private static final String MODEL = "{\"nodes\":["
            + "{\"key\":\"start\",\"type\":\"START_EVENT\",\"name\":\"发起\"},"
            + "{\"key\":\"n1\",\"type\":\"USER_TASK\",\"name\":\"部门审批\",\"assignee\":\"manager\"},"
            + "{\"key\":\"n2\",\"type\":\"USER_TASK\",\"name\":\"总监审批\",\"assignee\":\"director\"},"
            + "{\"key\":\"n3\",\"type\":\"USER_TASK\",\"name\":\"终审\",\"assignee\":\"boss\"},"
            + "{\"key\":\"c1\",\"type\":\"COUNTER_SIGN\",\"name\":\"会签\","
            + "\"assignees\":[\"a\",\"b\"],\"counterSignPolicy\":\"RATIO:60\"},"
            + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],\"flows\":["
            + "{\"key\":\"f1\",\"from\":\"start\",\"to\":\"n1\"},"
            + "{\"key\":\"f2\",\"from\":\"n1\",\"to\":\"n2\"},"
            + "{\"key\":\"f3\",\"from\":\"n1\",\"to\":\"c1\"},"
            + "{\"key\":\"f4\",\"from\":\"c1\",\"to\":\"end\"},"
            + "{\"key\":\"f5\",\"from\":\"n2\",\"to\":\"n3\"},"
            + "{\"key\":\"f6\",\"from\":\"n3\",\"to\":\"end\"}]}";

    @Mock
    private ProcessTaskMapper processTaskMapper;

    @Mock
    private ProcessInstanceMapper processInstanceMapper;

    @Mock
    private InstanceModelLoader instanceModelLoader;

    private TaskRejectOptionsCommand command;

    private ProcessInstancePo instance;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProcessTaskPo.class);
        TableInfoHelper.initTableInfo(assistant, ProcessInstancePo.class);
    }

    @BeforeEach
    void setUp() {
        command = new TaskRejectOptionsCommand(processTaskMapper,
                new TaskCommandSupport(processTaskMapper, processInstanceMapper),
                instanceModelLoader);
        instance = new ProcessInstancePo();
        instance.setId(10L);
        instance.setStatus(WorkflowConstants.INSTANCE_RUNNING);
        instance.setInitiator("alice");
        instance.setCurrentNodeKey("n2");
        when(processInstanceMapper.selectById(10L)).thenReturn(instance);
        when(instanceModelLoader.resolveInstanceJson(instance)).thenReturn(MODEL);
    }

    @Test
    @DisplayName("非首节点：可退发起人、可退唯一上一节点并列出候选")
    void shouldExposePreviousAndCandidates() {
        when(processTaskMapper.selectById(100L)).thenReturn(task(100L, "n2"));

        TaskRejectOptionsVO vo = command.options(100L);

        assertTrue(vo.isRejectable());
        assertTrue(vo.isInitiator());
        assertNotNull(vo.getPrevious());
        assertEquals("n1", vo.getPrevious().getNodeKey());
        assertEquals(1, vo.getCandidates().size());
        assertFalse(vo.isCounterSign());
    }

    @Test
    @DisplayName("多级上游：候选包含全部上游审批节点，上一节点仅取最近一层")
    void shouldExposeAllAncestorsAndNearestPrevious() {
        when(processTaskMapper.selectById(100L)).thenReturn(task(100L, "n3"));

        TaskRejectOptionsVO vo = command.options(100L);

        assertEquals("n2", vo.getPrevious().getNodeKey());
        assertEquals(2, vo.getCandidates().size());
        assertEquals("n1", vo.getCandidates().get(0).getNodeKey());
        assertEquals("n2", vo.getCandidates().get(1).getNodeKey());
    }

    @Test
    @DisplayName("首节点：上一节点为空，候选为空")
    void shouldExposeInitiatorOnlyOnFirstNode() {
        when(processTaskMapper.selectById(100L)).thenReturn(task(100L, "n1"));

        TaskRejectOptionsVO vo = command.options(100L);

        assertTrue(vo.isRejectable());
        assertTrue(vo.isInitiator());
        assertNull(vo.getPrevious());
        assertTrue(vo.getCandidates().isEmpty());
    }

    @Test
    @DisplayName("会签节点：返回策略并标记 counterSign")
    void shouldExposeCounterSignPolicy() {
        when(processTaskMapper.selectById(100L)).thenReturn(task(100L, "c1"));

        TaskRejectOptionsVO vo = command.options(100L);

        assertTrue(vo.isCounterSign());
        assertEquals("RATIO:60", vo.getPolicy());
        assertTrue(vo.isRejectable());
    }

    @Test
    @DisplayName("重新提交待办：不可驳回")
    void shouldNotRejectResubmitTask() {
        when(processTaskMapper.selectById(100L)).thenReturn(task(100L, WorkflowConstants.RESUBMIT_NODE_KEY));

        TaskRejectOptionsVO vo = command.options(100L);

        assertFalse(vo.isRejectable());
        assertTrue(vo.getReason().contains("发起节点"));
    }

    @Test
    @DisplayName("已处理任务：不可驳回")
    void shouldNotRejectHandledTask() {
        ProcessTaskPo handled = task(100L, "n2");
        handled.setStatus(WorkflowConstants.TASK_APPROVED);
        when(processTaskMapper.selectById(100L)).thenReturn(handled);

        assertFalse(command.options(100L).isRejectable());
    }

    @Test
    @DisplayName("任务不存在：抛 NOT_FOUND")
    void shouldFailWhenTaskMissing() {
        when(processTaskMapper.selectById(404L)).thenReturn(null);

        assertThrows(BizException.class, () -> command.options(404L));
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
