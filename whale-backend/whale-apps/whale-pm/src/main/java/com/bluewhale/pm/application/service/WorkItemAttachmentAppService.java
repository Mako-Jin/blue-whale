package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pm.application.assembler.PmAssembler;
import com.bluewhale.pm.domain.model.ActivityAction;
import com.bluewhale.pm.infrastructure.entity.PmAttachment;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.mapper.PmAttachmentMapper;
import com.bluewhale.pm.interfaces.dto.WorkItemAttachmentSaveRequest;
import com.bluewhale.pm.interfaces.vo.WorkItemAttachmentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工作项附件应用服务：只登记 whale-file 的 {@code file_id} 引用，不保存文件二进制。
 *
 * <p>删除附件仅解除 PM 侧引用，不删除 {@code whale-file} 中的文件记录。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkItemAttachmentAppService {

    private final PmAttachmentMapper attachmentMapper;

    private final ProjectGuard projectGuard;

    private final ProjectAccessGuard projectAccessGuard;

    private final WorkItemGuard workItemGuard;

    private final WorkItemActivityAppService activityAppService;

    /**
     * 查询工作项附件（按上传时间升序）。
     *
     * @param workItemId 工作项ID
     * @param operatorId 当前用户ID
     * @return 附件列表
     */
    public List<WorkItemAttachmentVO> list(Long workItemId, Long operatorId) {
        workItemGuard.requireReadable(workItemId, operatorId);
        return attachmentMapper.selectList(new LambdaQueryWrapper<PmAttachment>()
                .eq(PmAttachment::getWorkItemId, workItemId)
                .orderByAsc(PmAttachment::getCreateTime)
                .orderByAsc(PmAttachment::getId)).stream()
                .map(PmAssembler::toAttachmentVo)
                .toList();
    }

    /**
     * 登记工作项附件引用（同一工作项内同一文件不可重复登记）。
     *
     * @param workItemId 工作项ID
     * @param request    登记请求
     * @param operatorId 当前用户ID
     * @return 登记后的附件
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkItemAttachmentVO add(Long workItemId, WorkItemAttachmentSaveRequest request, Long operatorId) {
        PmWorkItem item = workItemGuard.requireWritable(workItemId, operatorId);
        projectGuard.requireActiveProject(item.getProjectId());
        Long exists = attachmentMapper.selectCount(new LambdaQueryWrapper<PmAttachment>()
                .eq(PmAttachment::getWorkItemId, workItemId)
                .eq(PmAttachment::getFileId, request.getFileId()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.application.file-already-been-registered-as-attachment", "该文件已登记为当前工作项附件");
        }
        PmAttachment attachment = new PmAttachment();
        attachment.setProjectId(item.getProjectId());
        attachment.setWorkItemId(workItemId);
        attachment.setFileId(request.getFileId());
        attachment.setFileName(request.getFileName().trim());
        attachment.setUploaderId(operatorId);
        attachmentMapper.insert(attachment);
        activityAppService.record(item.getProjectId(), workItemId, operatorId, ActivityAction.ATTACH,
                "file", null, attachment.getFileName());
        log.info("工作项附件已登记: itemId={}, fileId={}", workItemId, request.getFileId());
        return PmAssembler.toAttachmentVo(attachment);
    }

    /**
     * 解除工作项附件引用（上传人或项目管理员）。
     *
     * @param attachmentId 附件记录ID
     * @param operatorId   当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long attachmentId, Long operatorId) {
        PmAttachment attachment = attachmentId == null ? null : attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pm.application.attachment-found", "附件不存在");
        }
        PmWorkItem item = workItemGuard.requireWritable(attachment.getWorkItemId(), operatorId);
        projectGuard.requireActiveProject(item.getProjectId());
        if (!operatorId.equals(attachment.getUploaderId())) {
            projectAccessGuard.requireAdmin(item.getProjectId(), operatorId);
        }
        attachmentMapper.deleteById(attachmentId);
        activityAppService.record(item.getProjectId(), item.getId(), operatorId, ActivityAction.ATTACH,
                "file", attachment.getFileName(), null);
        log.info("工作项附件已解除: itemId={}, attachmentId={}", item.getId(), attachmentId);
    }
}
