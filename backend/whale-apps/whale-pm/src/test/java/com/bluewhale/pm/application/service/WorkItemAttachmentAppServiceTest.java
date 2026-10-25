package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pm.domain.model.ActivityAction;
import com.bluewhale.pm.infrastructure.entity.PmAttachment;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.mapper.PmAttachmentMapper;
import com.bluewhale.pm.interfaces.dto.WorkItemAttachmentSaveRequest;
import com.bluewhale.pm.interfaces.vo.WorkItemAttachmentVO;
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
 * {@link WorkItemAttachmentAppService} 附件用例单元测试。
 *
 * @author BlueWhale
 */
class WorkItemAttachmentAppServiceTest {

    private static final Long PROJECT_ID = 1L;

    private static final Long ITEM_ID = 3001L;

    private static final Long OPERATOR_ID = 9L;

    private final PmAttachmentMapper attachmentMapper = mock(PmAttachmentMapper.class);

    private final ProjectGuard projectGuard = mock(ProjectGuard.class);

    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);

    private final WorkItemGuard workItemGuard = mock(WorkItemGuard.class);

    private final WorkItemActivityAppService activityAppService = mock(WorkItemActivityAppService.class);

    private final WorkItemAttachmentAppService service = new WorkItemAttachmentAppService(attachmentMapper,
            projectGuard, projectAccessGuard, workItemGuard, activityAppService);

    /**
     * 初始化 MyBatis-Plus 实体元数据（lambda 列名缓存）
     */
    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PmAttachment.class);
    }

    @Test
    @DisplayName("add：同一文件重复登记拒绝")
    void shouldRejectDuplicatedFile() {
        when(workItemGuard.requireWritable(ITEM_ID, OPERATOR_ID)).thenReturn(item());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(attachmentMapper.selectCount(ArgumentMatchers.<Wrapper<PmAttachment>>any())).thenReturn(1L);

        assertThrows(BizException.class, () -> service.add(ITEM_ID, request(), OPERATOR_ID));

        verify(attachmentMapper, never()).insert(any(PmAttachment.class));
    }

    @Test
    @DisplayName("add：合法登记落库并记录动态")
    void shouldAddAttachment() {
        when(workItemGuard.requireWritable(ITEM_ID, OPERATOR_ID)).thenReturn(item());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(attachmentMapper.selectCount(ArgumentMatchers.<Wrapper<PmAttachment>>any())).thenReturn(0L);
        when(attachmentMapper.insert(any(PmAttachment.class))).thenAnswer(invocation -> {
            PmAttachment attachment = invocation.getArgument(0);
            attachment.setId(6001L);
            return 1;
        });

        WorkItemAttachmentVO vo = service.add(ITEM_ID, request(), OPERATOR_ID);

        ArgumentCaptor<PmAttachment> captor = ArgumentCaptor.forClass(PmAttachment.class);
        verify(attachmentMapper).insert(captor.capture());
        assertEquals(8801L, captor.getValue().getFileId());
        assertEquals("设计稿.png", captor.getValue().getFileName());
        assertEquals(OPERATOR_ID, captor.getValue().getUploaderId());
        assertEquals("6001", vo.getId());
        verify(activityAppService).record(eq(PROJECT_ID), eq(ITEM_ID), eq(OPERATOR_ID),
                eq(ActivityAction.ATTACH), eq("file"), ArgumentMatchers.<String>isNull(), eq("设计稿.png"));
    }

    @Test
    @DisplayName("delete：非上传者且非管理员拒绝")
    void shouldRejectDeleteByNonUploader() {
        PmAttachment attachment = attachment(6001L, 8L);
        when(attachmentMapper.selectById(6001L)).thenReturn(attachment);
        when(workItemGuard.requireWritable(ITEM_ID, OPERATOR_ID)).thenReturn(item());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(projectAccessGuard.requireAdmin(PROJECT_ID, OPERATOR_ID))
                .thenThrow(new BizException(ErrorCode.FORBIDDEN, "仅项目管理员可执行该操作"));

        assertThrows(BizException.class, () -> service.delete(6001L, OPERATOR_ID));

        verify(attachmentMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("delete：上传者本人解除成功")
    void shouldDeleteOwnAttachment() {
        when(attachmentMapper.selectById(6001L)).thenReturn(attachment(6001L, OPERATOR_ID));
        when(workItemGuard.requireWritable(ITEM_ID, OPERATOR_ID)).thenReturn(item());
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());

        service.delete(6001L, OPERATOR_ID);

        verify(attachmentMapper).deleteById(6001L);
    }

    @Test
    @DisplayName("delete：附件不存在返回 404")
    void shouldRejectMissingAttachment() {
        when(attachmentMapper.selectById(6001L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.delete(6001L, OPERATOR_ID));
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
     * 构造附件实体
     *
     * @param id         附件ID
     * @param uploaderId 上传人ID
     * @return 附件实体
     */
    private PmAttachment attachment(Long id, Long uploaderId) {
        PmAttachment attachment = new PmAttachment();
        attachment.setId(id);
        attachment.setProjectId(PROJECT_ID);
        attachment.setWorkItemId(ITEM_ID);
        attachment.setFileId(8801L);
        attachment.setFileName("设计稿.png");
        attachment.setUploaderId(uploaderId);
        return attachment;
    }

    /**
     * 构造附件登记请求
     *
     * @return 附件登记请求
     */
    private WorkItemAttachmentSaveRequest request() {
        WorkItemAttachmentSaveRequest request = new WorkItemAttachmentSaveRequest();
        request.setFileId(8801L);
        request.setFileName(" 设计稿.png ");
        return request;
    }
}
