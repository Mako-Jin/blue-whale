package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pm.domain.model.ActivityAction;
import com.bluewhale.pm.infrastructure.client.ItemSequenceGenerator;
import com.bluewhale.pm.infrastructure.entity.PmAttachment;
import com.bluewhale.pm.infrastructure.entity.PmComment;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmStatus;
import com.bluewhale.pm.infrastructure.entity.PmTransition;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.entity.PmWorkItemLink;
import com.bluewhale.pm.infrastructure.mapper.PmAttachmentMapper;
import com.bluewhale.pm.infrastructure.mapper.PmCommentMapper;
import com.bluewhale.pm.infrastructure.mapper.PmStatusMapper;
import com.bluewhale.pm.infrastructure.mapper.PmTransitionMapper;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemLinkMapper;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemMapper;
import com.bluewhale.pm.interfaces.dto.WorkItemQueryRequest;
import com.bluewhale.pm.interfaces.dto.WorkItemRankRequest;
import com.bluewhale.pm.interfaces.dto.WorkItemSaveRequest;
import com.bluewhale.pm.interfaces.dto.WorkItemTransitionRequest;
import com.bluewhale.pm.interfaces.vo.WorkItemVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkItemAppService} 工作项用例单元测试。
 *
 * @author BlueWhale
 */
class WorkItemAppServiceTest {

    private static final Long PROJECT_ID = 1L;

    private static final Long OPERATOR_ID = 9L;

    private static final Long TODO_STATUS_ID = 11L;

    private static final Long DONE_STATUS_ID = 12L;

    private final PmWorkItemMapper workItemMapper = mock(PmWorkItemMapper.class);

    private final PmStatusMapper statusMapper = mock(PmStatusMapper.class);

    private final PmTransitionMapper transitionMapper = mock(PmTransitionMapper.class);

    private final PmCommentMapper commentMapper = mock(PmCommentMapper.class);

    private final PmAttachmentMapper attachmentMapper = mock(PmAttachmentMapper.class);

    private final PmWorkItemLinkMapper linkMapper = mock(PmWorkItemLinkMapper.class);

    private final ProjectGuard projectGuard = mock(ProjectGuard.class);

    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);

    private final WorkItemGuard workItemGuard = mock(WorkItemGuard.class);

    private final ItemSequenceGenerator sequenceGenerator = mock(ItemSequenceGenerator.class);

    private final WorkItemActivityAppService activityAppService = mock(WorkItemActivityAppService.class);

    private final WorkItemAppService service = new WorkItemAppService(workItemMapper, statusMapper,
            transitionMapper, commentMapper, attachmentMapper, linkMapper, projectGuard, projectAccessGuard,
            workItemGuard, sequenceGenerator, activityAppService);

    /**
     * 初始化 MyBatis-Plus 实体元数据（lambda 列名缓存）
     */
    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PmWorkItem.class);
        TableInfoHelper.initTableInfo(assistant, PmStatus.class);
        TableInfoHelper.initTableInfo(assistant, PmTransition.class);
        TableInfoHelper.initTableInfo(assistant, PmComment.class);
        TableInfoHelper.initTableInfo(assistant, PmAttachment.class);
        TableInfoHelper.initTableInfo(assistant, PmWorkItemLink.class);
    }

    @Test
    @DisplayName("create：同一 clientRequestId 返回首次结果")
    void shouldReturnExistingItemWhenIdempotent() {
        PmWorkItem existing = item(3001L, TODO_STATUS_ID, 0);
        existing.setItemKey("BW-1");
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(workItemMapper.selectOne(ArgumentMatchers.<Wrapper<PmWorkItem>>any())).thenReturn(existing);
        when(statusMapper.selectById(TODO_STATUS_ID)).thenReturn(status(TODO_STATUS_ID, "TODO"));
        WorkItemSaveRequest request = saveRequest("TASK", null);
        request.setClientRequestId("req-1");

        WorkItemVO vo = service.create(request, OPERATOR_ID);

        assertEquals("3001", vo.getId());
        verify(workItemMapper, never()).insert(any(PmWorkItem.class));
    }

    @Test
    @DisplayName("create：子任务未指定父工作项拒绝")
    void shouldRejectSubtaskWithoutParent() {
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());

        assertThrows(BizException.class, () -> service.create(saveRequest("SUBTASK", null), OPERATOR_ID));

        verify(workItemMapper, never()).insert(any(PmWorkItem.class));
    }

    @Test
    @DisplayName("create：正常落库并生成键与排序值")
    void shouldCreateWorkItem() {
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(statusMapper.selectOne(ArgumentMatchers.<Wrapper<PmStatus>>any()))
                .thenReturn(status(TODO_STATUS_ID, "TODO"));
        when(sequenceGenerator.next(PROJECT_ID)).thenReturn(1L);
        when(workItemMapper.insert(any(PmWorkItem.class))).thenAnswer(invocation -> {
            PmWorkItem item = invocation.getArgument(0);
            item.setId(3001L);
            return 1;
        });

        WorkItemVO vo = service.create(saveRequest("TASK", null), OPERATOR_ID);

        ArgumentCaptor<PmWorkItem> captor = ArgumentCaptor.forClass(PmWorkItem.class);
        verify(workItemMapper).insert(captor.capture());
        PmWorkItem saved = captor.getValue();
        assertEquals("BW-1", saved.getItemKey());
        assertEquals(1, saved.getItemNo());
        assertEquals("MEDIUM", saved.getPriority());
        assertEquals("000000001000", saved.getRankValue());
        assertEquals(OPERATOR_ID, saved.getReporterId());
        assertEquals("BW-1", vo.getKey());
        verify(activityAppService).record(eq(PROJECT_ID), eq(3001L), eq(OPERATOR_ID), eq(ActivityAction.CREATE),
                ArgumentMatchers.<String>isNull(), ArgumentMatchers.<String>isNull(), eq("BW-1"));
    }

    @Test
    @DisplayName("update：直接修改状态被拒绝")
    void shouldRejectStatusDirectUpdate() {
        PmWorkItem existing = item(3001L, TODO_STATUS_ID, 0);
        when(workItemGuard.requireWritable(3001L, OPERATOR_ID)).thenReturn(existing);
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        WorkItemSaveRequest request = saveRequest("TASK", null);
        request.setStatusId(DONE_STATUS_ID);

        assertThrows(BizException.class, () -> service.update(3001L, request, OPERATOR_ID));
    }

    @Test
    @DisplayName("update：版本号不匹配拒绝")
    void shouldRejectStaleVersion() {
        PmWorkItem existing = item(3001L, TODO_STATUS_ID, 2);
        when(workItemGuard.requireWritable(3001L, OPERATOR_ID)).thenReturn(existing);
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        WorkItemSaveRequest request = saveRequest("TASK", null);
        request.setVersion(1);

        assertThrows(BizException.class, () -> service.update(3001L, request, OPERATOR_ID));
    }

    @Test
    @DisplayName("update：乐观锁更新失败返回重复提交")
    void shouldRejectWhenOptimisticLockMissed() {
        PmWorkItem existing = item(3001L, TODO_STATUS_ID, 0);
        when(workItemGuard.requireWritable(3001L, OPERATOR_ID)).thenReturn(existing);
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(workItemMapper.update(ArgumentMatchers.<PmWorkItem>isNull(), ArgumentMatchers.any()))
                .thenReturn(0);

        assertThrows(BizException.class,
                () -> service.update(3001L, saveRequest("TASK", null), OPERATOR_ID));
    }

    @Test
    @DisplayName("delete：存在子工作项拒绝删除")
    void shouldRejectDeleteWithChildren() {
        PmWorkItem existing = item(3001L, TODO_STATUS_ID, 0);
        when(workItemGuard.requireWritable(3001L, OPERATOR_ID)).thenReturn(existing);
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(workItemMapper.selectCount(ArgumentMatchers.<Wrapper<PmWorkItem>>any())).thenReturn(2L);

        assertThrows(BizException.class, () -> service.delete(3001L, OPERATOR_ID));

        verify(workItemMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("transition：未配置流转边拒绝")
    void shouldRejectUnconfiguredTransition() {
        PmWorkItem existing = item(3001L, TODO_STATUS_ID, 0);
        when(workItemGuard.requireWritable(3001L, OPERATOR_ID)).thenReturn(existing);
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(statusMapper.selectById(DONE_STATUS_ID)).thenReturn(status(DONE_STATUS_ID, "DONE"));
        when(transitionMapper.selectOne(ArgumentMatchers.<Wrapper<PmTransition>>any())).thenReturn(null);

        assertThrows(BizException.class,
                () -> service.transition(3001L, transitionRequest(DONE_STATUS_ID, null), OPERATOR_ID));
    }

    @Test
    @DisplayName("transition：要求评论但未填写拒绝")
    void shouldRejectTransitionWithoutRequiredComment() {
        PmWorkItem existing = item(3001L, TODO_STATUS_ID, 0);
        when(workItemGuard.requireWritable(3001L, OPERATOR_ID)).thenReturn(existing);
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(statusMapper.selectById(DONE_STATUS_ID)).thenReturn(status(DONE_STATUS_ID, "DONE"));
        when(transitionMapper.selectOne(ArgumentMatchers.<Wrapper<PmTransition>>any()))
                .thenReturn(transition(1));

        assertThrows(BizException.class,
                () -> service.transition(3001L, transitionRequest(DONE_STATUS_ID, " "), OPERATOR_ID));
    }

    @Test
    @DisplayName("transition：合法流转更新状态并记录动态")
    void shouldTransitionWorkItem() {
        PmWorkItem existing = item(3001L, TODO_STATUS_ID, 0);
        when(workItemGuard.requireWritable(3001L, OPERATOR_ID)).thenReturn(existing);
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(statusMapper.selectById(DONE_STATUS_ID)).thenReturn(status(DONE_STATUS_ID, "DONE"));
        when(transitionMapper.selectOne(ArgumentMatchers.<Wrapper<PmTransition>>any()))
                .thenReturn(transition(0));
        when(workItemMapper.update(ArgumentMatchers.<PmWorkItem>isNull(), ArgumentMatchers.any()))
                .thenReturn(1);
        when(statusMapper.selectById(TODO_STATUS_ID)).thenReturn(status(TODO_STATUS_ID, "TODO"));

        WorkItemVO vo = service.transition(3001L, transitionRequest(DONE_STATUS_ID, "完成"), OPERATOR_ID);

        assertEquals("12", vo.getStatusId());
        assertEquals("DONE", vo.getStatusCode());
        verify(commentMapper).insert(any(com.bluewhale.pm.infrastructure.entity.PmComment.class));
        verify(activityAppService).record(eq(PROJECT_ID), eq(3001L), eq(OPERATOR_ID),
                eq(ActivityAction.TRANSITION), eq("status"), eq("TODO"), eq("DONE"));
    }

    @Test
    @DisplayName("rank：两个锚点同时为空拒绝")
    void shouldRejectRankWithoutAnchor() {
        PmWorkItem existing = item(3001L, TODO_STATUS_ID, 0);
        when(workItemGuard.requireWritable(3001L, OPERATOR_ID)).thenReturn(existing);
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());

        assertThrows(BizException.class, () -> service.rank(3001L, new WorkItemRankRequest(), OPERATOR_ID));
    }

    @Test
    @DisplayName("rank：锚点顺序倒置拒绝")
    void shouldRejectReversedAnchors() {
        PmWorkItem existing = item(3001L, TODO_STATUS_ID, 0);
        when(workItemGuard.requireWritable(3001L, OPERATOR_ID)).thenReturn(existing);
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        PmWorkItem before = item(3002L, TODO_STATUS_ID, 0);
        before.setRankValue("000000002000");
        PmWorkItem after = item(3003L, TODO_STATUS_ID, 0);
        after.setRankValue("000000001000");
        when(workItemGuard.requireInProject(3002L, PROJECT_ID)).thenReturn(before);
        when(workItemGuard.requireInProject(3003L, PROJECT_ID)).thenReturn(after);

        WorkItemRankRequest request = new WorkItemRankRequest();
        request.setBeforeItemId(3002L);
        request.setAfterItemId(3003L);

        assertThrows(BizException.class, () -> service.rank(3001L, request, OPERATOR_ID));
    }

    @Test
    @DisplayName("page：非成员读取被拒绝")
    void shouldRejectPageForNonMember() {
        when(projectAccessGuard.requireReadable(PROJECT_ID, OPERATOR_ID))
                .thenThrow(new BizException(com.bluewhale.common.core.exception.ErrorCode.FORBIDDEN, "非项目成员"));

        assertThrows(BizException.class, () -> service.page(queryRequest(), OPERATOR_ID));
    }

    /**
     * 构造项目实体
     *
     * @return 项目实体
     */
    private PmProject project() {
        PmProject project = new PmProject();
        project.setId(PROJECT_ID);
        project.setProjectKey("BW");
        project.setStatus(ProjectGuard.STATUS_ACTIVE);
        return project;
    }

    /**
     * 构造工作项实体
     *
     * @param id       工作项ID
     * @param statusId 状态ID
     * @param version  版本号
     * @return 工作项实体
     */
    private PmWorkItem item(Long id, Long statusId, int version) {
        PmWorkItem item = new PmWorkItem();
        item.setId(id);
        item.setProjectId(PROJECT_ID);
        item.setStatusId(statusId);
        item.setVersion(version);
        item.setTitle("标题");
        item.setType("TASK");
        item.setPriority("MEDIUM");
        item.setRankValue("000000001000");
        return item;
    }

    /**
     * 构造状态实体
     *
     * @param id   状态ID
     * @param code 状态编码
     * @return 状态实体
     */
    private PmStatus status(Long id, String code) {
        PmStatus status = new PmStatus();
        status.setId(id);
        status.setProjectId(PROJECT_ID);
        status.setCode(code);
        status.setName(code);
        status.setCategory("DONE".equals(code) ? "DONE" : "TODO");
        return status;
    }

    /**
     * 构造流转实体
     *
     * @param requireComment 是否要求评论
     * @return 流转实体
     */
    private PmTransition transition(int requireComment) {
        PmTransition transition = new PmTransition();
        transition.setProjectId(PROJECT_ID);
        transition.setFromStatusId(TODO_STATUS_ID);
        transition.setToStatusId(DONE_STATUS_ID);
        transition.setRequireComment(requireComment);
        return transition;
    }

    /**
     * 构造工作项保存请求
     *
     * @param type     类型
     * @param parentId 父工作项ID
     * @return 保存请求
     */
    private WorkItemSaveRequest saveRequest(String type, Long parentId) {
        WorkItemSaveRequest request = new WorkItemSaveRequest();
        request.setProjectId(PROJECT_ID);
        request.setType(type);
        request.setTitle("标题");
        request.setParentId(parentId);
        return request;
    }

    /**
     * 构造流转请求
     *
     * @param targetStatusId 目标状态ID
     * @param comment        流转评论
     * @return 流转请求
     */
    private WorkItemTransitionRequest transitionRequest(Long targetStatusId, String comment) {
        WorkItemTransitionRequest request = new WorkItemTransitionRequest();
        request.setTargetStatusId(targetStatusId);
        request.setComment(comment);
        return request;
    }

    /**
     * 构造分页查询请求
     *
     * @return 分页查询请求
     */
    private WorkItemQueryRequest queryRequest() {
        WorkItemQueryRequest request = new WorkItemQueryRequest();
        request.setProjectId(PROJECT_ID);
        return request;
    }
}
