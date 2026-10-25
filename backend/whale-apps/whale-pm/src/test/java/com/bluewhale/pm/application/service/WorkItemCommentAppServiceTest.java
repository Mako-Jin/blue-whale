package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pm.domain.model.ActivityAction;
import com.bluewhale.pm.infrastructure.entity.PmComment;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.mapper.PmCommentMapper;
import com.bluewhale.pm.interfaces.dto.WorkItemCommentSaveRequest;
import com.bluewhale.pm.interfaces.vo.WorkItemCommentVO;
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
 * {@link WorkItemCommentAppService} 评论用例单元测试。
 *
 * @author BlueWhale
 */
class WorkItemCommentAppServiceTest {

    private static final Long PROJECT_ID = 1L;

    private static final Long ITEM_ID = 3001L;

    private static final Long OPERATOR_ID = 9L;

    private final PmCommentMapper commentMapper = mock(PmCommentMapper.class);

    private final ProjectGuard projectGuard = mock(ProjectGuard.class);

    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);

    private final WorkItemGuard workItemGuard = mock(WorkItemGuard.class);

    private final WorkItemActivityAppService activityAppService = mock(WorkItemActivityAppService.class);

    private final WorkItemCommentAppService service = new WorkItemCommentAppService(commentMapper, projectGuard,
            projectAccessGuard, workItemGuard, activityAppService);

    /**
     * 初始化 MyBatis-Plus 实体元数据（lambda 列名缓存）
     */
    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PmComment.class);
    }

    @Test
    @DisplayName("add：父评论不属于当前工作项拒绝")
    void shouldRejectCrossItemParent() {
        when(workItemGuard.requireWritable(ITEM_ID, OPERATOR_ID)).thenReturn(item());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        PmComment parent = comment(5001L, 3999L, 8L, "旧评论");
        when(commentMapper.selectById(5001L)).thenReturn(parent);

        WorkItemCommentSaveRequest request = request("回复", 5001L);

        assertThrows(BizException.class, () -> service.add(ITEM_ID, request, OPERATOR_ID));

        verify(commentMapper, never()).insert(any(PmComment.class));
    }

    @Test
    @DisplayName("add：合法评论落库并记录动态")
    void shouldAddComment() {
        when(workItemGuard.requireWritable(ITEM_ID, OPERATOR_ID)).thenReturn(item());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(commentMapper.insert(any(PmComment.class))).thenAnswer(invocation -> {
            PmComment comment = invocation.getArgument(0);
            comment.setId(5002L);
            return 1;
        });

        WorkItemCommentVO vo = service.add(ITEM_ID, request(" 进展顺利 ", null), OPERATOR_ID);

        ArgumentCaptor<PmComment> captor = ArgumentCaptor.forClass(PmComment.class);
        verify(commentMapper).insert(captor.capture());
        assertEquals("进展顺利", captor.getValue().getContent());
        assertEquals(PROJECT_ID, captor.getValue().getProjectId());
        assertEquals(OPERATOR_ID, captor.getValue().getAuthorId());
        assertEquals("5002", vo.getId());
        verify(activityAppService).record(eq(PROJECT_ID), eq(ITEM_ID), eq(OPERATOR_ID),
                eq(ActivityAction.COMMENT), eq("content"), ArgumentMatchers.<String>isNull(), eq("进展顺利"));
    }

    @Test
    @DisplayName("delete：非作者且非管理员拒绝")
    void shouldRejectDeleteByNonAuthor() {
        when(commentMapper.selectById(5001L)).thenReturn(comment(5001L, ITEM_ID, 8L, "他人评论"));
        when(workItemGuard.requireReadable(ITEM_ID, OPERATOR_ID)).thenReturn(item());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(projectAccessGuard.requireAdmin(PROJECT_ID, OPERATOR_ID))
                .thenThrow(new BizException(ErrorCode.FORBIDDEN, "仅项目管理员可执行该操作"));

        assertThrows(BizException.class, () -> service.delete(5001L, OPERATOR_ID));

        verify(commentMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("delete：作者本人删除成功")
    void shouldDeleteOwnComment() {
        when(commentMapper.selectById(5001L)).thenReturn(comment(5001L, ITEM_ID, OPERATOR_ID, "我的评论"));
        when(workItemGuard.requireReadable(ITEM_ID, OPERATOR_ID)).thenReturn(item());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(commentMapper.delete(ArgumentMatchers.<Wrapper<PmComment>>any())).thenReturn(1);

        service.delete(5001L, OPERATOR_ID);

        verify(commentMapper).deleteById(5001L);
    }

    @Test
    @DisplayName("delete：评论不存在返回 404")
    void shouldRejectMissingComment() {
        when(commentMapper.selectById(5001L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.delete(5001L, OPERATOR_ID));
    }

    /**
     * 构造工作项实体
     *
     * @return 工作项实体
     */
    private PmWorkItem item() {
        PmWorkItem item = new PmWorkItem();
        item.setId(ITEM_ID);
        item.setProjectId(PROJECT_ID);
        return item;
    }

    /**
     * 构造项目实体
     *
     * @return 项目实体
     */
    private PmProject project() {
        PmProject project = new PmProject();
        project.setId(PROJECT_ID);
        project.setStatus(ProjectGuard.STATUS_ACTIVE);
        return project;
    }

    /**
     * 构造评论实体
     *
     * @param id         评论ID
     * @param workItemId 工作项ID
     * @param authorId   作者ID
     * @param content    内容
     * @return 评论实体
     */
    private PmComment comment(Long id, Long workItemId, Long authorId, String content) {
        PmComment comment = new PmComment();
        comment.setId(id);
        comment.setProjectId(PROJECT_ID);
        comment.setWorkItemId(workItemId);
        comment.setAuthorId(authorId);
        comment.setContent(content);
        return comment;
    }

    /**
     * 构造评论请求
     *
     * @param content  内容
     * @param parentId 父评论ID
     * @return 评论请求
     */
    private WorkItemCommentSaveRequest request(String content, Long parentId) {
        WorkItemCommentSaveRequest request = new WorkItemCommentSaveRequest();
        request.setContent(content);
        request.setParentId(parentId);
        return request;
    }
}
