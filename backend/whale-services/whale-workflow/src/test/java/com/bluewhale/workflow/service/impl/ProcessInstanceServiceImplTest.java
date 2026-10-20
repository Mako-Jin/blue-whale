package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.ProcessInstanceQueryRequest;
import com.bluewhale.workflow.dto.ProcessInstanceStartRequest;
import com.bluewhale.workflow.entity.ProcessDefinition;
import com.bluewhale.workflow.entity.ProcessInstance;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.mapper.ProcessDefinitionMapper;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.vo.ProcessInstanceVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProcessInstanceServiceImpl} 流程实例发起 / 详情 / 分页 / 撤销单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ProcessInstanceServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    /** 含两个审批节点的流程定义 JSON */
    private static final String TWO_NODE_JSON = "{\"nodes\":[{\"key\":\"n1\",\"name\":\"一级审批\",\"assignee\":\"admin\"},"
            + "{\"key\":\"n2\",\"name\":\"二级审批\",\"assignee\":\"user\"}]}";

    /** 规范化定义：开始事件 → t1 → t2 → 结束事件 */
    private static final String NORMALIZED_JSON =
            "{\"type\":\"stateMachine\",\"nodes\":["
                    + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                    + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"name\":\"一级审批\",\"assignee\":\"admin\"},"
                    + "{\"key\":\"t2\",\"type\":\"USER_TASK\",\"name\":\"二级审批\",\"assignee\":\"user\"},"
                    + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                    + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"},"
                    + "{\"key\":\"f2\",\"from\":\"t1\",\"to\":\"t2\"},{\"key\":\"f3\",\"from\":\"t2\",\"to\":\"end\"}]}";

    /** 规范化定义：开始事件直达结束事件（无审批节点） */
    private static final String START_END_JSON =
            "{\"type\":\"stateMachine\",\"nodes\":[{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                    + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                    + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"end\"}]}";

    @Mock
    private ProcessDefinitionMapper processDefinitionMapper;

    @Mock
    private ProcessInstanceMapper processInstanceMapper;

    @Mock
    private ProcessTaskMapper processTaskMapper;

    @InjectMocks
    private ProcessInstanceServiceImpl processInstanceService;

    /**
     * 构造流程定义桩对象。
     *
     * @param id 流程定义ID
     * @param definitionJson 流程定义JSON
     * @return 流程定义实体
     */
    private static ProcessDefinition definition(Long id, String definitionJson) {
        ProcessDefinition definition = new ProcessDefinition();
        definition.setId(id);
        definition.setCode("wf-a");
        definition.setVersion(1);
        definition.setDefinitionJson(definitionJson);
        return definition;
    }

    /**
     * 构造发起请求桩对象。
     *
     * @param definitionId 流程定义ID
     * @return 发起请求
     */
    private static ProcessInstanceStartRequest startRequest(Long definitionId) {
        ProcessInstanceStartRequest request = new ProcessInstanceStartRequest();
        request.setDefinitionId(definitionId);
        request.setTitle("报销流程");
        request.setBusinessKey("B-1");
        return request;
    }

    @Test
    @DisplayName("start：定义存在且含节点时建实例并生成首节点待办")
    void shouldStartWhenDefinitionValid() {
        when(processDefinitionMapper.selectById(10L)).thenReturn(definition(10L, TWO_NODE_JSON));

        ProcessInstanceVO vo = processInstanceService.start(startRequest(10L), "zhangsan");

        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(processInstanceMapper).insert(instanceCaptor.capture());
        assertEquals(10L, instanceCaptor.getValue().getDefinitionId());
        assertEquals("wf-a", instanceCaptor.getValue().getDefinitionCode());
        assertEquals("报销流程", instanceCaptor.getValue().getTitle());
        assertEquals("B-1", instanceCaptor.getValue().getBusinessKey());
        assertEquals(0, instanceCaptor.getValue().getStatus().intValue());
        assertEquals(0, instanceCaptor.getValue().getCurrentIndex().intValue());
        assertEquals("zhangsan", instanceCaptor.getValue().getInitiator());

        ArgumentCaptor<ProcessTask> taskCaptor = ArgumentCaptor.forClass(ProcessTask.class);
        verify(processTaskMapper).insert(taskCaptor.capture());
        assertEquals(0, taskCaptor.getValue().getNodeIndex().intValue());
        assertEquals("一级审批", taskCaptor.getValue().getNodeName());
        assertEquals("admin", taskCaptor.getValue().getAssignee());
        assertEquals(0, taskCaptor.getValue().getStatus().intValue());

        assertEquals(0, vo.getStatus().intValue());
        assertEquals(0, vo.getCurrentIndex().intValue());
        assertEquals("zhangsan", vo.getInitiator());
    }

    @Test
    @DisplayName("start：定义不存在抛 NOT_FOUND 且不落库")
    void shouldThrowWhenStartDefinitionMissing() {
        when(processDefinitionMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> processInstanceService.start(startRequest(99L), "zhangsan"));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(processInstanceMapper, never()).insert(any(ProcessInstance.class));
        verify(processTaskMapper, never()).insert(any(ProcessTask.class));
    }

    @Test
    @DisplayName("start：定义无有效节点抛参数错误且不落库")
    void shouldThrowWhenDefinitionHasNoNode() {
        when(processDefinitionMapper.selectById(10L)).thenReturn(definition(10L, "{\"nodes\":[]}"));

        BizException ex = assertThrows(BizException.class,
                () -> processInstanceService.start(startRequest(10L), "zhangsan"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("流程定义缺少有效审批节点", ex.getMessage());
        verify(processInstanceMapper, never()).insert(any(ProcessInstance.class));
    }

    @Test
    @DisplayName("getDetail：返回实例详情并附带任务列表")
    void shouldGetDetailWithTasks() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(1L);
        instance.setStatus(0);
        when(processInstanceMapper.selectById(1L)).thenReturn(instance);
        ProcessTask task = new ProcessTask();
        task.setId(100L);
        task.setInstanceId(1L);
        task.setNodeIndex(0);
        task.setAssignee("admin");
        when(processTaskMapper.selectList(ArgumentMatchers.<Wrapper<ProcessTask>>any())).thenReturn(List.of(task));

        ProcessInstanceVO vo = processInstanceService.getDetail(1L);

        assertEquals(1L, vo.getId());
        assertEquals(1, vo.getTasks().size());
        assertEquals("admin", vo.getTasks().get(0).getAssignee());
    }

    @Test
    @DisplayName("getDetail：实例不存在抛 NOT_FOUND")
    void shouldThrowWhenDetailMissing() {
        when(processInstanceMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> processInstanceService.getDetail(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("cancel：运行中实例撤销并取消其余待办")
    void shouldCancelWhenRunning() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(5L);
        instance.setStatus(0);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance);

        processInstanceService.cancel(5L);

        verify(processInstanceMapper).updateById(instance);
        assertEquals(3, instance.getStatus().intValue());
        verify(processTaskMapper).update(any(ProcessTask.class), ArgumentMatchers.<Wrapper<ProcessTask>>any());
    }

    @Test
    @DisplayName("cancel：非运行中实例抛参数错误且不更新")
    void shouldThrowWhenCancelNotRunning() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(5L);
        instance.setStatus(1);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance);

        BizException ex = assertThrows(BizException.class, () -> processInstanceService.cancel(5L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("实例非运行中", ex.getMessage());
        verify(processInstanceMapper, never()).updateById(any(ProcessInstance.class));
    }

    @Test
    @DisplayName("page：按条件调用分页查询并映射结果")
    void shouldPageWithConditions() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(1L);
        instance.setStatus(0);
        Page<ProcessInstance> page = new Page<>(1L, 10L);
        page.setRecords(List.of(instance));
        page.setTotal(1L);
        when(processInstanceMapper.selectPage(ArgumentMatchers.<Page<ProcessInstance>>any(),
                ArgumentMatchers.<Wrapper<ProcessInstance>>any())).thenReturn(page);
        ProcessInstanceQueryRequest request = new ProcessInstanceQueryRequest();
        request.setTitle("报销");
        request.setStatus(0);
        request.setDefinitionCode("wf-a");

        PageResult<ProcessInstanceVO> result = processInstanceService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getRecords().get(0).getId());
    }

    @Test
    @DisplayName("start：规范化定义透传开始事件并以节点键记录当前状态")
    void shouldStartNormalizedAtFirstUserTaskKey() {
        when(processDefinitionMapper.selectById(10L)).thenReturn(definition(10L, NORMALIZED_JSON));

        ProcessInstanceVO vo = processInstanceService.start(startRequest(10L), "zhangsan");

        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(processInstanceMapper).insert(instanceCaptor.capture());
        assertEquals(0, instanceCaptor.getValue().getStatus().intValue());
        assertEquals(1, instanceCaptor.getValue().getCurrentIndex().intValue());
        assertEquals("t1", instanceCaptor.getValue().getCurrentNodeKey());

        ArgumentCaptor<ProcessTask> taskCaptor = ArgumentCaptor.forClass(ProcessTask.class);
        verify(processTaskMapper).insert(taskCaptor.capture());
        assertEquals(1, taskCaptor.getValue().getNodeIndex().intValue());
        assertEquals("admin", taskCaptor.getValue().getAssignee());
        assertEquals("t1", vo.getCurrentNodeKey());
    }

    @Test
    @DisplayName("start：无审批节点时实例直接置为已通过且不生成待办")
    void shouldApproveInstanceWithoutUserTask() {
        when(processDefinitionMapper.selectById(10L)).thenReturn(definition(10L, START_END_JSON));

        ProcessInstanceVO vo = processInstanceService.start(startRequest(10L), "zhangsan");

        assertEquals(1, vo.getStatus().intValue());
        verify(processInstanceMapper).insert(any(ProcessInstance.class));
        verify(processTaskMapper, never()).insert(any(ProcessTask.class));
    }
}
