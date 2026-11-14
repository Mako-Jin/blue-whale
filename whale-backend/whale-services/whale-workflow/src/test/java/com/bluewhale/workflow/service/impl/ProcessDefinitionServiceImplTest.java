package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.DefinitionMigrationRequest;
import com.bluewhale.workflow.dto.ProcessDefinitionSaveRequest;
import com.bluewhale.workflow.dto.ProcessQueryRequest;
import com.bluewhale.workflow.entity.ProcessDefinition;
import com.bluewhale.workflow.entity.ProcessDefinitionVersion;
import com.bluewhale.workflow.entity.ProcessInstance;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.mapper.ProcessDefinitionMapper;
import com.bluewhale.workflow.mapper.ProcessDefinitionVersionMapper;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.support.WorkflowMetrics;
import com.bluewhale.workflow.vo.DefinitionMigrationVO;
import com.bluewhale.workflow.vo.ProcessDefinitionVO;
import com.bluewhale.workflow.vo.ProcessDefinitionVersionVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProcessDefinitionServiceImpl} 流程定义增删改查、版本快照、发布与实例迁移单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ProcessDefinitionServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    /** 规范化定义：开始事件 → t1 → 结束事件 */
    private static final String V1_JSON =
            "{\"type\":\"stateMachine\",\"nodes\":["
                    + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                    + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"name\":\"一级审批\",\"assignee\":\"admin\"},"
                    + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                    + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"},"
                    + "{\"key\":\"f2\",\"from\":\"t1\",\"to\":\"end\"}]}";

    @Mock
    private ProcessDefinitionMapper processDefinitionMapper;

    @Mock
    private ProcessDefinitionVersionMapper processDefinitionVersionMapper;

    @Mock
    private ProcessInstanceMapper processInstanceMapper;

    @Mock
    private ProcessTaskMapper processTaskMapper;

    @Mock
    private WorkflowMetrics workflowMetrics;

    @InjectMocks
    private ProcessDefinitionServiceImpl processDefinitionService;

    /**
     * 构造流程定义保存请求桩对象。
     *
     * @param code 流程编码
     * @param name 流程名称
     * @return 保存请求
     */
    private static ProcessDefinitionSaveRequest saveRequest(String code, String name) {
        ProcessDefinitionSaveRequest request = new ProcessDefinitionSaveRequest();
        request.setCode(code);
        request.setName(name);
        return request;
    }

    @Test
    @DisplayName("create：编码唯一时落库并返回视图对象（默认版本 / 状态）")
    void shouldCreateWhenCodeUnique() {
        when(processDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<ProcessDefinition>>any())).thenReturn(0L);

        ProcessDefinitionVO vo = processDefinitionService.create(saveRequest("wf-a", "A流程"));

        ArgumentCaptor<ProcessDefinition> captor = ArgumentCaptor.forClass(ProcessDefinition.class);
        verify(processDefinitionMapper).insert(captor.capture());
        assertEquals("wf-a", captor.getValue().getCode());
        assertEquals("A流程", captor.getValue().getName());
        assertEquals(1, captor.getValue().getVersion().intValue());
        assertEquals(1, captor.getValue().getStatus().intValue());
        assertEquals("wf-a", vo.getCode());
    }

    @Test
    @DisplayName("create：并发写入触发唯一键冲突时转换为编码已存在")
    void shouldConvertDuplicateKeyToBizException() {
        when(processDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<ProcessDefinition>>any())).thenReturn(0L);
        when(processDefinitionMapper.insert(any(ProcessDefinition.class)))
                .thenThrow(new DuplicateKeyException("uk_tenant_active_code"));

        BizException ex = assertThrows(BizException.class,
                () -> processDefinitionService.create(saveRequest("wf-a", "A流程")));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("流程编码已存在", ex.getMessage());
        verify(workflowMetrics).recordCodeConflict();
    }

    @Test
    @DisplayName("create：编码已存在时抛参数错误且不落库")
    void shouldRejectDuplicateCode() {
        when(processDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<ProcessDefinition>>any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> processDefinitionService.create(saveRequest("wf-a", "A流程")));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("流程编码已存在", ex.getMessage());
        verify(processDefinitionMapper, never()).insert(any(ProcessDefinition.class));
    }

    @Test
    @DisplayName("create：definition_json 超过 64KB 抛参数错误且不落库")
    void shouldRejectOversizeDefinition() {
        when(processDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<ProcessDefinition>>any())).thenReturn(0L);
        ProcessDefinitionSaveRequest request = saveRequest("wf-a", "A流程");
        request.setDefinitionJson("x".repeat(65537));

        BizException ex = assertThrows(BizException.class, () -> processDefinitionService.create(request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(processDefinitionMapper, never()).insert(any(ProcessDefinition.class));
    }

    @Test
    @DisplayName("update：存在时更新字段并返回视图对象")
    void shouldUpdateWhenExists() {
        ProcessDefinition entity = new ProcessDefinition();
        entity.setId(1L);
        entity.setCode("wf-a");
        when(processDefinitionMapper.selectById(1L)).thenReturn(entity);
        ProcessDefinitionSaveRequest request = saveRequest("wf-a", "A流程新版");
        request.setDefinitionJson("{\"nodes\":[]}");
        request.setRemark("备注");

        ProcessDefinitionVO vo = processDefinitionService.update(1L, request);

        verify(processDefinitionMapper).updateById(entity);
        assertEquals("A流程新版", entity.getName());
        assertEquals("{\"nodes\":[]}", entity.getDefinitionJson());
        assertEquals("备注", vo.getRemark());
    }

    @Test
    @DisplayName("update：内容变更但版本号未前进时自动递增并只写草稿快照")
    void shouldBumpVersionAndWriteDraftWhenJsonChangedWithSameVersion() {
        ProcessDefinition entity = new ProcessDefinition();
        entity.setId(1L);
        entity.setCode("wf-a");
        entity.setVersion(3);
        entity.setDefinitionJson("{\"nodes\":[{\"key\":\"a\"}]}");
        when(processDefinitionMapper.selectById(1L)).thenReturn(entity);
        ProcessDefinitionSaveRequest request = saveRequest("wf-a", "A流程");
        request.setDefinitionJson("{\"nodes\":[{\"key\":\"b\"}]}");
        request.setVersion(3);

        processDefinitionService.update(1L, request);

        assertEquals(4, entity.getVersion().intValue());
        ArgumentCaptor<ProcessDefinitionVersion> captor = ArgumentCaptor.forClass(ProcessDefinitionVersion.class);
        verify(processDefinitionVersionMapper).insert(captor.capture());
        assertEquals(4, captor.getValue().getVersion().intValue());
        // 快照发布状态必须为草稿（0），发布态只能由 publish() 显式切换
        assertEquals(0, captor.getValue().getPublishStatus().intValue());
    }

    @Test
    @DisplayName("update：ID 不存在抛 NOT_FOUND 且不更新")
    void shouldThrowWhenUpdateMissing() {
        when(processDefinitionMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> processDefinitionService.update(99L, saveRequest("wf-a", "A流程")));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(processDefinitionMapper, never()).updateById(any(ProcessDefinition.class));
    }

    @Test
    @DisplayName("delete：ID 不存在抛 NOT_FOUND 且不删除")
    void shouldThrowWhenDeleteMissing() {
        when(processDefinitionMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> processDefinitionService.delete(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(processDefinitionMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    @DisplayName("getDetail：存在时返回视图对象")
    void shouldGetDetailWhenExists() {
        ProcessDefinition entity = new ProcessDefinition();
        entity.setId(1L);
        entity.setCode("wf-a");
        entity.setName("A流程");
        when(processDefinitionMapper.selectById(1L)).thenReturn(entity);

        ProcessDefinitionVO vo = processDefinitionService.getDetail(1L);

        assertEquals(1L, vo.getId());
        assertEquals("wf-a", vo.getCode());
        assertEquals("A流程", vo.getName());
    }

    @Test
    @DisplayName("page：按条件调用分页查询并映射结果")
    void shouldPageWithConditions() {
        ProcessDefinition entity = new ProcessDefinition();
        entity.setId(1L);
        entity.setCode("wf-a");
        entity.setName("A流程");
        Page<ProcessDefinition> page = new Page<>(1L, 10L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(processDefinitionMapper.selectPage(ArgumentMatchers.<Page<ProcessDefinition>>any(),
                ArgumentMatchers.<Wrapper<ProcessDefinition>>any())).thenReturn(page);
        ProcessQueryRequest request = new ProcessQueryRequest();
        request.setName("A");
        request.setCode("wf-a");
        request.setStatus(1);

        PageResult<ProcessDefinitionVO> result = processDefinitionService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("wf-a", result.getRecords().get(0).getCode());
        verify(processDefinitionMapper).selectPage(ArgumentMatchers.<Page<ProcessDefinition>>any(),
                ArgumentMatchers.<Wrapper<ProcessDefinition>>any());
    }

    @Test
    @DisplayName("create：写入已发布版本快照")
    void shouldWritePublishedSnapshotOnCreate() {
        when(processDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<ProcessDefinition>>any())).thenReturn(0L);

        processDefinitionService.create(saveRequest("wf-a", "A流程"));

        ArgumentCaptor<ProcessDefinitionVersion> captor = ArgumentCaptor.forClass(ProcessDefinitionVersion.class);
        verify(processDefinitionVersionMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getVersion().intValue());
        assertEquals(1, captor.getValue().getPublishStatus().intValue());
    }

    @Test
    @DisplayName("update：definition_json 变更时版本自增并写草稿快照")
    void shouldBumpVersionAndWriteDraftSnapshot() {
        ProcessDefinition entity = new ProcessDefinition();
        entity.setId(1L);
        entity.setCode("wf-a");
        entity.setVersion(3);
        entity.setDefinitionJson("{\"nodes\":[]}");
        when(processDefinitionMapper.selectById(1L)).thenReturn(entity);
        ProcessDefinitionSaveRequest request = saveRequest("wf-a", "A流程");
        request.setDefinitionJson(V1_JSON);

        processDefinitionService.update(1L, request);

        assertEquals(4, entity.getVersion().intValue());
        ArgumentCaptor<ProcessDefinitionVersion> captor = ArgumentCaptor.forClass(ProcessDefinitionVersion.class);
        verify(processDefinitionVersionMapper).insert(captor.capture());
        assertEquals(4, captor.getValue().getVersion().intValue());
        assertEquals(0, captor.getValue().getPublishStatus().intValue());
    }

    @Test
    @DisplayName("publish：切换定义表内容并只保留目标版本发布态")
    void shouldPublishTargetVersion() {
        ProcessDefinition entity = new ProcessDefinition();
        entity.setId(1L);
        entity.setCode("wf-a");
        entity.setVersion(3);
        entity.setDefinitionJson("{\"nodes\":[]}");
        when(processDefinitionMapper.selectById(1L)).thenReturn(entity);
        ProcessDefinitionVersion target = version(1L, 2, V1_JSON, 0);
        target.setId(20L);
        ProcessDefinitionVersion other = version(1L, 1, "{\"nodes\":[]}", 1);
        other.setId(10L);
        when(processDefinitionVersionMapper.selectList(ArgumentMatchers.<Wrapper<ProcessDefinitionVersion>>any()))
                .thenReturn(List.of(target), List.of(other));

        ProcessDefinitionVO vo = processDefinitionService.publish(1L, 2);

        assertEquals(2, vo.getVersion().intValue());
        assertEquals(V1_JSON, vo.getDefinitionJson());
        assertEquals(1, target.getPublishStatus().intValue());
        assertEquals(0, other.getPublishStatus().intValue());
        verify(processDefinitionVersionMapper).updateById(target);
        verify(processDefinitionVersionMapper).updateById(other);
        verify(processDefinitionMapper).updateById(entity);
    }

    @Test
    @DisplayName("publish：版本不存在时抛参数错误且不切换")
    void shouldRejectPublishMissingVersion() {
        ProcessDefinition entity = new ProcessDefinition();
        entity.setId(1L);
        entity.setCode("wf-a");
        when(processDefinitionMapper.selectById(1L)).thenReturn(entity);

        BizException ex = assertThrows(BizException.class, () -> processDefinitionService.publish(1L, 9));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(processDefinitionMapper, never()).updateById(any(ProcessDefinition.class));
    }

    @Test
    @DisplayName("versions：返回版本快照列表并映射视图对象")
    void shouldListVersions() {
        ProcessDefinition entity = new ProcessDefinition();
        entity.setId(1L);
        entity.setCode("wf-a");
        when(processDefinitionMapper.selectById(1L)).thenReturn(entity);
        when(processDefinitionVersionMapper.selectList(ArgumentMatchers.<Wrapper<ProcessDefinitionVersion>>any()))
                .thenReturn(List.of(version(1L, 2, V1_JSON, 0), version(1L, 1, "{}", 1)));

        List<ProcessDefinitionVersionVO> versions = processDefinitionService.versions(1L);

        assertEquals(2, versions.size());
        assertEquals(2, versions.get(0).getVersion().intValue());
        assertEquals(V1_JSON, versions.get(0).getDefinitionJson());
    }

    @Test
    @DisplayName("migrate：运行中实例锁定目标版本并同步待办")
    void shouldMigrateRunningInstance() {
        ProcessDefinition entity = new ProcessDefinition();
        entity.setId(1L);
        entity.setCode("wf-a");
        when(processDefinitionMapper.selectById(1L)).thenReturn(entity);
        when(processDefinitionVersionMapper.selectList(ArgumentMatchers.<Wrapper<ProcessDefinitionVersion>>any()))
                .thenReturn(List.of(version(1L, 2, V1_JSON, 1)));
        ProcessInstance running = instance(100L, 1L, 0, 1, "t1");
        ProcessInstance done = instance(101L, 1L, 1, 1, "t1");
        when(processInstanceMapper.selectList(ArgumentMatchers.<Wrapper<ProcessInstance>>any()))
                .thenReturn(List.of(running, done));
        ProcessTask task = new ProcessTask();
        task.setId(200L);
        task.setInstanceId(100L);
        task.setNodeIndex(0);
        task.setNodeKey("t1");
        task.setStatus(0);
        when(processTaskMapper.selectList(ArgumentMatchers.<Wrapper<ProcessTask>>any())).thenReturn(List.of(task));
        DefinitionMigrationRequest request = new DefinitionMigrationRequest();
        request.setTargetVersion(2);

        DefinitionMigrationVO vo = processDefinitionService.migrate(1L, request);

        assertEquals(1, vo.getMigrated().intValue());
        assertEquals(1, vo.getSkipped().intValue());
        assertEquals(0, vo.getFailed().intValue());
        assertEquals(2, running.getDefinitionVersion().intValue());
        assertEquals(1, running.getCurrentIndex().intValue());
        assertEquals("一级审批", task.getNodeName());
        verify(processTaskMapper).updateById(task);
    }

    @Test
    @DisplayName("migrate：目标版本缺节点时记失败且不更新实例")
    void shouldFailMigrationWhenNodeMissing() {
        ProcessDefinition entity = new ProcessDefinition();
        entity.setId(1L);
        entity.setCode("wf-a");
        when(processDefinitionMapper.selectById(1L)).thenReturn(entity);
        when(processDefinitionVersionMapper.selectList(ArgumentMatchers.<Wrapper<ProcessDefinitionVersion>>any()))
                .thenReturn(List.of(version(1L, 2, V1_JSON, 1)));
        ProcessInstance running = instance(100L, 1L, 0, 1, "t9");
        when(processInstanceMapper.selectList(ArgumentMatchers.<Wrapper<ProcessInstance>>any()))
                .thenReturn(List.of(running));
        DefinitionMigrationRequest request = new DefinitionMigrationRequest();
        request.setTargetVersion(2);

        DefinitionMigrationVO vo = processDefinitionService.migrate(1L, request);

        assertEquals(0, vo.getMigrated().intValue());
        assertEquals(1, vo.getFailed().intValue());
        assertTrue(vo.getFailures().get(0).contains("t9"));
        assertEquals(1, running.getDefinitionVersion().intValue());
        verify(processInstanceMapper, never()).updateById(any(ProcessInstance.class));
    }

    /**
     * 构造版本快照桩对象。
     *
     * @param definitionId 流程定义ID
     * @param version 版本号
     * @param definitionJson 流程定义JSON
     * @param publishStatus 发布状态
     * @return 版本快照实体
     */
    private static ProcessDefinitionVersion version(Long definitionId, int version, String definitionJson,
                                                   int publishStatus) {
        ProcessDefinitionVersion snapshot = new ProcessDefinitionVersion();
        snapshot.setDefinitionId(definitionId);
        snapshot.setVersion(version);
        snapshot.setDefinitionJson(definitionJson);
        snapshot.setPublishStatus(publishStatus);
        return snapshot;
    }

    /**
     * 构造流程实例桩对象。
     *
     * @param id 实例ID
     * @param definitionId 流程定义ID
     * @param status 实例状态
     * @param definitionVersion 锁定的定义版本
     * @param currentNodeKey 当前节点键
     * @return 流程实例实体
     */
    private static ProcessInstance instance(Long id, Long definitionId, int status, int definitionVersion,
                                            String currentNodeKey) {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(id);
        instance.setDefinitionId(definitionId);
        instance.setStatus(status);
        instance.setDefinitionVersion(definitionVersion);
        instance.setCurrentNodeKey(currentNodeKey);
        return instance;
    }
}
