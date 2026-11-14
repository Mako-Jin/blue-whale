package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.request.ApprovalRequest;
import com.bluewhale.workflow.request.ProcessTaskQueryRequest;
import com.bluewhale.workflow.po.ProcessDefinitionPo;
import com.bluewhale.workflow.po.ProcessDefinitionVersionPo;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.mapper.ProcessDefinitionMapper;
import com.bluewhale.workflow.mapper.ProcessDefinitionVersionMapper;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.service.ProcessCcService;
import com.bluewhale.workflow.service.ProcessDelegateService;
import com.bluewhale.workflow.service.ProcessTaskService;
import com.bluewhale.workflow.service.impl.task.AddSignCommand;
import com.bluewhale.workflow.service.impl.task.ApproveCommand;
import com.bluewhale.workflow.service.impl.task.CompleteCommand;
import com.bluewhale.workflow.service.impl.task.CounterSignCommand;
import com.bluewhale.workflow.service.impl.task.DelegateCommand;
import com.bluewhale.workflow.service.impl.task.InstanceModelLoader;
import com.bluewhale.workflow.service.impl.task.RejectCommand;
import com.bluewhale.workflow.service.impl.task.RollbackCommand;
import com.bluewhale.workflow.service.impl.task.TaskActivationCommand;
import com.bluewhale.workflow.service.impl.task.TaskCommandSupport;
import com.bluewhale.workflow.service.impl.task.TaskQueryCommand;
import com.bluewhale.workflow.service.impl.task.TransferCommand;
import com.bluewhale.workflow.support.WorkflowMetrics;
import com.bluewhale.workflow.vo.ProcessTaskVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProcessTaskServiceImpl} 待办任务分页 / 同意推进 / 驳回取消单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ProcessTaskServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;

    /** 含两个审批节点的流程定义 JSON */
    private static final String TWO_NODE_JSON = "{\"nodes\":[{\"key\":\"n1\",\"name\":\"一级审批\",\"assignee\":\"admin\"},"
            + "{\"key\":\"n2\",\"name\":\"二级审批\",\"assignee\":\"user\"}]}";

    /** 仅含一个审批节点的流程定义 JSON */
    private static final String ONE_NODE_JSON = "{\"nodes\":[{\"key\":\"n1\",\"name\":\"一级审批\",\"assignee\":\"admin\"}]}";

    /** 规范化定义：开始事件 → t1 → t2 → 结束事件 */
    private static final String NORMALIZED_JSON =
            "{\"type\":\"stateMachine\",\"nodes\":["
                    + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                    + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"name\":\"一级审批\",\"assignee\":\"admin\"},"
                    + "{\"key\":\"t2\",\"type\":\"USER_TASK\",\"name\":\"二级审批\",\"assignee\":\"user\"},"
                    + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                    + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"},"
                    + "{\"key\":\"f2\",\"from\":\"t1\",\"to\":\"t2\"},{\"key\":\"f3\",\"from\":\"t2\",\"to\":\"end\"}]}";

    /** 规范化定义：二级审批节点绑定表单 */
    private static final String FORM_JSON =
            "{\"type\":\"stateMachine\",\"nodes\":["
                    + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                    + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"name\":\"一级审批\",\"assignee\":\"admin\"},"
                    + "{\"key\":\"t2\",\"type\":\"USER_TASK\",\"name\":\"二级审批\",\"assignee\":\"user\","
                    + "\"formId\":\"2112355156699869186\",\"formCode\":\"bw_d29_form\"},"
                    + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                    + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"},"
                    + "{\"key\":\"f2\",\"from\":\"t1\",\"to\":\"t2\"},{\"key\":\"f3\",\"from\":\"t2\",\"to\":\"end\"}]}";

    /** legacy 定义：节点无 key（归一化时合成 {@code n{i+1}}） */
    private static final String LEGACY_NO_KEY_JSON =
            "{\"nodes\":[{\"name\":\"一级审批\",\"assignee\":\"admin\"},{\"name\":\"二级审批\",\"assignee\":\"user\"}]}";

    @Mock
    private ProcessTaskMapper processTaskMapper;

    @Mock
    private ProcessInstanceMapper processInstanceMapper;

    @Mock
    private ProcessDefinitionMapper processDefinitionMapper;

    @Mock
    private ProcessDefinitionVersionMapper processDefinitionVersionMapper;

    @Mock
    private ProcessDelegateService processDelegateService;

    @Mock
    private ProcessCcService processCcService;

    @Mock
    private WorkflowMetrics workflowMetrics;

    private ProcessTaskService processTaskService;

    /**
     * 以 Mock Mapper 组装真实命令对象与委派门面（事务注解由 Spring 代理承担，单测只验证委托行为）。
     */
    @BeforeEach
    void setUp() {
        TaskCommandSupport support = new TaskCommandSupport(processTaskMapper, processInstanceMapper);
        InstanceModelLoader loader = new InstanceModelLoader(processDefinitionMapper, processDefinitionVersionMapper);
        CounterSignCommand counterSignCommand = new CounterSignCommand(processTaskMapper);
        TaskActivationCommand activationCommand = new TaskActivationCommand(
                processTaskMapper, processDelegateService, processCcService);
        ApproveCommand approveCommand = new ApproveCommand(processInstanceMapper, support,
                loader, counterSignCommand, activationCommand);
        RejectCommand rejectCommand = new RejectCommand(processInstanceMapper, support);
        processTaskService = new ProcessTaskServiceImpl(
                new TaskQueryCommand(processTaskMapper),
                approveCommand,
                rejectCommand,
                new TransferCommand(processTaskMapper, support),
                new DelegateCommand(processTaskMapper, support),
                new RollbackCommand(processInstanceMapper, support, activationCommand, loader),
                new AddSignCommand(processTaskMapper, support),
                new CompleteCommand(processTaskMapper, approveCommand, rejectCommand),
                workflowMetrics);
    }

    /**
     * 构造待办任务桩对象。
     *
     * @param id 任务ID
     * @param instanceId 所属实例ID
     * @param status 任务状态
     * @return 待办任务实体
     */
    private static ProcessTaskPo pendingTask(Long id, Long instanceId, int status) {
        ProcessTaskPo task = new ProcessTaskPo();
        task.setId(id);
        task.setInstanceId(instanceId);
        task.setNodeIndex(0);
        task.setAssignee("admin");
        task.setStatus(status);
        return task;
    }

    /**
     * 构造流程实例桩对象。
     *
     * @param id 实例ID
     * @param definitionId 流程定义ID
     * @param status 实例状态
     * @param currentIndex 当前节点下标
     * @return 流程实例实体
     */
    private static ProcessInstancePo instance(Long id, Long definitionId, int status, int currentIndex) {
        ProcessInstancePo instance = new ProcessInstancePo();
        instance.setId(id);
        instance.setDefinitionId(definitionId);
        instance.setStatus(status);
        instance.setCurrentIndex(currentIndex);
        return instance;
    }

    /**
     * 构造带当前节点键的流程实例桩对象。
     *
     * @param id 实例ID
     * @param definitionId 流程定义ID
     * @param status 实例状态
     * @param currentIndex 当前节点下标
     * @param currentNodeKey 当前节点键
     * @return 流程实例实体
     */
    private static ProcessInstancePo keyedInstance(Long id, Long definitionId, int status, int currentIndex, String currentNodeKey) {
        ProcessInstancePo instance = instance(id, definitionId, status, currentIndex);
        instance.setCurrentNodeKey(currentNodeKey);
        return instance;
    }

    /**
     * 构造流程定义桩对象。
     *
     * @param id 流程定义ID
     * @param definitionJson 流程定义JSON
     * @return 流程定义实体
     */
    private static ProcessDefinitionPo definition(Long id, String definitionJson) {
        ProcessDefinitionPo definition = new ProcessDefinitionPo();
        definition.setId(id);
        definition.setDefinitionJson(definitionJson);
        return definition;
    }

    @Test
    @DisplayName("approve：非末节点同意后推进下标并生成下一节点待办")
    void shouldAdvanceWhenApproveNotLastNode() {
        ProcessTaskPo task = pendingTask(100L, 5L, 0);
        ProcessInstancePo instance = instance(5L, 10L, 0, 0);
        when(processTaskMapper.selectById(100L)).thenReturn(task);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance);
        when(processDefinitionMapper.selectById(10L)).thenReturn(definition(10L, TWO_NODE_JSON));
        when(processTaskMapper.update(any(ProcessTaskPo.class), ArgumentMatchers.<Wrapper<ProcessTaskPo>>any()))
                .thenReturn(1);

        ApprovalRequest approval = new ApprovalRequest();
        approval.setComment("同意");
        processTaskService.approve(100L, approval);

        assertEquals(1, task.getStatus().intValue());
        assertEquals("同意", task.getComment());
        verify(processTaskMapper).update(any(ProcessTaskPo.class), ArgumentMatchers.<Wrapper<ProcessTaskPo>>any());
        assertEquals(1, instance.getCurrentIndex().intValue());
        assertEquals(0, instance.getStatus().intValue());
        verify(processInstanceMapper).updateById(instance);
        ArgumentCaptor<ProcessTaskPo> captor = ArgumentCaptor.forClass(ProcessTaskPo.class);
        verify(processTaskMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getNodeIndex().intValue());
        assertEquals("user", captor.getValue().getAssignee());
        assertEquals(0, captor.getValue().getStatus().intValue());
    }

    @Test
    @DisplayName("approve：优先按实例锁定版本的快照推进")
    void shouldPreferSnapshotOnApprove() {
        ProcessTaskPo task = pendingTask(100L, 5L, 0);
        task.setNodeKey("t1");
        ProcessInstancePo instance = keyedInstance(5L, 10L, 0, 1, "t1");
        instance.setDefinitionVersion(1);
        when(processTaskMapper.selectById(100L)).thenReturn(task);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance);
        ProcessDefinitionVersionPo snapshot = new ProcessDefinitionVersionPo();
        snapshot.setDefinitionId(10L);
        snapshot.setVersion(1);
        snapshot.setDefinitionJson(NORMALIZED_JSON);
        when(processDefinitionVersionMapper.selectList(ArgumentMatchers.<Wrapper<ProcessDefinitionVersionPo>>any()))
                .thenReturn(List.of(snapshot));
        when(processTaskMapper.update(any(ProcessTaskPo.class), ArgumentMatchers.<Wrapper<ProcessTaskPo>>any()))
                .thenReturn(1);

        processTaskService.approve(100L, null);

        assertEquals(2, instance.getCurrentIndex().intValue());
        assertEquals("t2", instance.getCurrentNodeKey());
        ArgumentCaptor<ProcessTaskPo> captor = ArgumentCaptor.forClass(ProcessTaskPo.class);
        verify(processTaskMapper).insert(captor.capture());
        assertEquals("user", captor.getValue().getAssignee());
    }

    @Test
    @DisplayName("approve：末节点同意后实例置为已通过且不再生成待办")
    void shouldApproveInstanceWhenLastNode() {
        ProcessTaskPo task = pendingTask(100L, 5L, 0);
        ProcessInstancePo instance = instance(5L, 10L, 0, 0);
        when(processTaskMapper.selectById(100L)).thenReturn(task);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance);
        when(processDefinitionMapper.selectById(10L)).thenReturn(definition(10L, ONE_NODE_JSON));
        when(processTaskMapper.update(any(ProcessTaskPo.class), ArgumentMatchers.<Wrapper<ProcessTaskPo>>any()))
                .thenReturn(1);

        processTaskService.approve(100L, null);

        assertEquals(1, task.getStatus().intValue());
        assertEquals(1, instance.getStatus().intValue());
        verify(processInstanceMapper).updateById(instance);
        verify(processTaskMapper, never()).insert(any(ProcessTaskPo.class));
    }

    @Test
    @DisplayName("approve：任务已处理抛参数错误且不推进")
    void shouldThrowWhenApproveHandledTask() {
        ProcessTaskPo task = pendingTask(100L, 5L, 1);
        when(processTaskMapper.selectById(100L)).thenReturn(task);

        BizException ex = assertThrows(BizException.class, () -> processTaskService.approve(100L, null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("任务已处理", ex.getMessage());
        verify(processTaskMapper, never()).updateById(any(ProcessTaskPo.class));
    }

    @Test
    @DisplayName("approve：实例非运行中抛参数错误且不推进")
    void shouldThrowWhenApproveOnNotRunningInstance() {
        ProcessTaskPo task = pendingTask(100L, 5L, 0);
        ProcessInstancePo instance = instance(5L, 10L, 1, 0);
        when(processTaskMapper.selectById(100L)).thenReturn(task);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance);

        BizException ex = assertThrows(BizException.class, () -> processTaskService.approve(100L, null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("实例非运行中", ex.getMessage());
        verify(processTaskMapper, never()).updateById(any(ProcessTaskPo.class));
    }

    @Test
    @DisplayName("reject：驳回后实例置为已驳回并取消其余待办")
    void shouldRejectInstanceAndCancelOthers() {
        ProcessTaskPo task = pendingTask(100L, 5L, 0);
        ProcessInstancePo instance = instance(5L, 10L, 0, 0);
        when(processTaskMapper.selectById(100L)).thenReturn(task);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance);
        when(processTaskMapper.update(any(ProcessTaskPo.class), ArgumentMatchers.<Wrapper<ProcessTaskPo>>any()))
                .thenReturn(1);

        processTaskService.reject(100L, "不同意");

        assertEquals(2, task.getStatus().intValue());
        assertEquals("不同意", task.getComment());
        assertEquals(2, instance.getStatus().intValue());
        verify(processInstanceMapper).updateById(instance);
        verify(processTaskMapper, times(2))
                .update(any(ProcessTaskPo.class), ArgumentMatchers.<Wrapper<ProcessTaskPo>>any());
    }

    @Test
    @DisplayName("page：按条件调用分页查询并映射结果")
    void shouldPageWithConditions() {
        ProcessTaskPo task = pendingTask(100L, 5L, 0);
        Page<ProcessTaskPo> page = new Page<>(1L, 10L);
        page.setRecords(List.of(task));
        page.setTotal(1L);
        when(processTaskMapper.selectPage(ArgumentMatchers.<Page<ProcessTaskPo>>any(),
                ArgumentMatchers.<Wrapper<ProcessTaskPo>>any())).thenReturn(page);
        ProcessTaskQueryRequest request = new ProcessTaskQueryRequest();
        request.setAssignee("admin");
        request.setStatus(0);
        request.setInstanceId(5L);

        PageResult<ProcessTaskVO> result = processTaskService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("admin", result.getRecords().get(0).getAssignee());
    }

    @Test
    @DisplayName("approve：以 current_node_key 为准推进（不依赖 currentIndex）")
    void shouldAdvanceByCurrentNodeKey() {
        ProcessTaskPo task = pendingTask(100L, 5L, 0);
        ProcessInstancePo instance = keyedInstance(5L, 10L, 0, 2, "t1");
        when(processTaskMapper.selectById(100L)).thenReturn(task);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance);
        when(processDefinitionMapper.selectById(10L)).thenReturn(definition(10L, NORMALIZED_JSON));
        when(processTaskMapper.update(any(ProcessTaskPo.class), ArgumentMatchers.<Wrapper<ProcessTaskPo>>any()))
                .thenReturn(1);

        processTaskService.approve(100L, null);

        ArgumentCaptor<ProcessTaskPo> captor = ArgumentCaptor.forClass(ProcessTaskPo.class);
        verify(processTaskMapper).insert(captor.capture());
        assertEquals("user", captor.getValue().getAssignee());
        assertEquals("t2", instance.getCurrentNodeKey());
        assertEquals(2, instance.getCurrentIndex().intValue());
        assertEquals(0, instance.getStatus().intValue());
    }

    @Test
    @DisplayName("approve：current_node_key 为空时回落 currentIndex 解析")
    void shouldFallbackToCurrentIndex() {
        ProcessTaskPo task = pendingTask(100L, 5L, 0);
        ProcessInstancePo instance = instance(5L, 10L, 0, 1);
        when(processTaskMapper.selectById(100L)).thenReturn(task);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance);
        when(processDefinitionMapper.selectById(10L)).thenReturn(definition(10L, NORMALIZED_JSON));
        when(processTaskMapper.update(any(ProcessTaskPo.class), ArgumentMatchers.<Wrapper<ProcessTaskPo>>any()))
                .thenReturn(1);

        processTaskService.approve(100L, null);

        ArgumentCaptor<ProcessTaskPo> captor = ArgumentCaptor.forClass(ProcessTaskPo.class);
        verify(processTaskMapper).insert(captor.capture());
        assertEquals("user", captor.getValue().getAssignee());
        assertEquals("t2", instance.getCurrentNodeKey());
    }

    @Test
    @DisplayName("approve：末节点以节点键推进到结束事件并置为已通过")
    void shouldApproveInstanceWhenKeyedLastNode() {
        ProcessTaskPo task = pendingTask(100L, 5L, 0);
        ProcessInstancePo instance = keyedInstance(5L, 10L, 0, 2, "t2");
        when(processTaskMapper.selectById(100L)).thenReturn(task);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance);
        when(processDefinitionMapper.selectById(10L)).thenReturn(definition(10L, NORMALIZED_JSON));
        when(processTaskMapper.update(any(ProcessTaskPo.class), ArgumentMatchers.<Wrapper<ProcessTaskPo>>any()))
                .thenReturn(1);

        processTaskService.approve(100L, null);

        assertEquals(1, instance.getStatus().intValue());
        assertEquals("end", instance.getCurrentNodeKey());
        verify(processTaskMapper, never()).insert(any(ProcessTaskPo.class));
    }

    @Test
    @DisplayName("approve：生成下一节点待办时回填 nodeKey/formId/formCode")
    void shouldBackfillNodeKeyAndFormOnCreateTask() {
        ProcessTaskPo task = pendingTask(100L, 5L, 0);
        ProcessInstancePo instance = keyedInstance(5L, 10L, 0, 1, "t1");
        when(processTaskMapper.selectById(100L)).thenReturn(task);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance);
        when(processDefinitionMapper.selectById(10L)).thenReturn(definition(10L, FORM_JSON));
        when(processTaskMapper.update(any(ProcessTaskPo.class), ArgumentMatchers.<Wrapper<ProcessTaskPo>>any()))
                .thenReturn(1);

        processTaskService.approve(100L, null);

        ArgumentCaptor<ProcessTaskPo> captor = ArgumentCaptor.forClass(ProcessTaskPo.class);
        verify(processTaskMapper).insert(captor.capture());
        assertEquals("t2", captor.getValue().getNodeKey());
        assertEquals("2112355156699869186", captor.getValue().getFormId());
        assertEquals("bw_d29_form", captor.getValue().getFormCode());
    }

    @Test
    @DisplayName("approve：legacy 无 key 节点生成待办时 nodeKey 合成 n{i+1}")
    void shouldSynthesizeNodeKeyForLegacyOnCreateTask() {
        ProcessTaskPo task = pendingTask(100L, 5L, 0);
        ProcessInstancePo instance = instance(5L, 10L, 0, 0);
        when(processTaskMapper.selectById(100L)).thenReturn(task);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance);
        when(processDefinitionMapper.selectById(10L)).thenReturn(definition(10L, LEGACY_NO_KEY_JSON));
        when(processTaskMapper.update(any(ProcessTaskPo.class), ArgumentMatchers.<Wrapper<ProcessTaskPo>>any()))
                .thenReturn(1);

        processTaskService.approve(100L, null);

        ArgumentCaptor<ProcessTaskPo> captor = ArgumentCaptor.forClass(ProcessTaskPo.class);
        verify(processTaskMapper).insert(captor.capture());
        assertEquals("n2", captor.getValue().getNodeKey());
        assertNull(captor.getValue().getFormId());
        assertNull(captor.getValue().getFormCode());
    }

    @Test
    @DisplayName("addSign：为当前节点追加去重后的加签待办，归属人为发起加签人")
    void shouldAddSignTasks() {
        ProcessTaskPo task = pendingTask(100L, 5L, 0);
        task.setNodeKey("t1");
        task.setNodeName("一级审批");
        when(processTaskMapper.selectById(100L)).thenReturn(task);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance(5L, 10L, 0, 0));
        when(processTaskMapper.selectList(ArgumentMatchers.<Wrapper<ProcessTaskPo>>any()))
                .thenReturn(List.of(task));

        processTaskService.addSign(100L, List.of(" lisi ", "wangwu", "lisi"));

        ArgumentCaptor<ProcessTaskPo> captor = ArgumentCaptor.forClass(ProcessTaskPo.class);
        verify(processTaskMapper, times(2)).insert(captor.capture());
        assertEquals(List.of("lisi", "wangwu"),
                captor.getAllValues().stream().map(ProcessTaskPo::getAssignee).toList());
        for (ProcessTaskPo added : captor.getAllValues()) {
            assertEquals(0, added.getStatus().intValue());
            assertEquals("ADD_SIGN", added.getSignType());
            assertEquals(added.getAssignee(), added.getOwnerId());
            assertEquals("t1", added.getNodeKey());
            assertEquals("一级审批", added.getNodeName());
        }
    }

    @Test
    @DisplayName("addSign：加签人已在本节点任务中抛参数错误")
    void shouldThrowWhenAddSignExistingAssignee() {
        ProcessTaskPo task = pendingTask(100L, 5L, 0);
        task.setNodeKey("t1");
        when(processTaskMapper.selectById(100L)).thenReturn(task);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance(5L, 10L, 0, 0));
        when(processTaskMapper.selectList(ArgumentMatchers.<Wrapper<ProcessTaskPo>>any()))
                .thenReturn(List.of(task));

        BizException ex = assertThrows(BizException.class,
                () -> processTaskService.addSign(100L, List.of("admin")));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("加签人已在本节点任务中：admin", ex.getMessage());
        verify(processTaskMapper, never()).insert(any(ProcessTaskPo.class));
    }

    @Test
    @DisplayName("addSign：ANY 会签节点不支持加签")
    void shouldRejectAddSignOnAnyCounterSign() {
        ProcessTaskPo task = pendingTask(100L, 5L, 0);
        task.setNodeKey("t1");
        task.setSignType("COUNTER_SIGN");
        task.setSignPolicy("ANY");
        when(processTaskMapper.selectById(100L)).thenReturn(task);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance(5L, 10L, 0, 0));

        BizException ex = assertThrows(BizException.class,
                () -> processTaskService.addSign(100L, List.of("lisi")));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("ANY/RATIO 会签节点不支持加签", ex.getMessage());
        verify(processTaskMapper, never()).insert(any(ProcessTaskPo.class));
    }

    @Test
    @DisplayName("approve：同节点仍有待办（加签）时等待不推进")
    void shouldWaitWhenSameNodeHasPendingTask() {
        ProcessTaskPo task = pendingTask(100L, 5L, 0);
        task.setNodeKey("t1");
        ProcessInstancePo instance = keyedInstance(5L, 10L, 0, 1, "t1");
        when(processTaskMapper.selectById(100L)).thenReturn(task);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance);
        when(processDefinitionMapper.selectById(10L)).thenReturn(definition(10L, NORMALIZED_JSON));
        when(processTaskMapper.selectCount(ArgumentMatchers.<Wrapper<ProcessTaskPo>>any()))
                .thenReturn(1L);
        when(processTaskMapper.update(any(ProcessTaskPo.class), ArgumentMatchers.<Wrapper<ProcessTaskPo>>any()))
                .thenReturn(1);

        processTaskService.approve(100L, null);

        assertEquals(1, task.getStatus().intValue());
        verify(processTaskMapper, never()).insert(any(ProcessTaskPo.class));
        assertEquals("t1", instance.getCurrentNodeKey());
        assertEquals(0, instance.getStatus().intValue());
    }
}
