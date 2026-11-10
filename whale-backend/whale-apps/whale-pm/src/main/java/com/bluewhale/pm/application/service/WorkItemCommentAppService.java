package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pm.application.assembler.PmAssembler;
import com.bluewhale.pm.application.notify.PmNotifyEvent;
import com.bluewhale.pm.application.notify.PmNotifyKind;
import com.bluewhale.pm.application.notify.PmNotifyPublisher;
import com.bluewhale.pm.domain.model.ActivityAction;
import com.bluewhale.pm.infrastructure.entity.PmComment;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.mapper.PmCommentMapper;
import com.bluewhale.pm.interfaces.dto.WorkItemCommentSaveRequest;
import com.bluewhale.pm.interfaces.vo.WorkItemCommentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工作项评论应用服务：列表、新增与删除。
 *
 * <p>删除权限为「评论作者」或「项目管理员」；删除父评论时级联删除其回复。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkItemCommentAppService {

    /** 通知明细最大长度 */
    private static final int MAX_DETAIL_LENGTH = 200;

    private final PmCommentMapper commentMapper;

    private final ProjectGuard projectGuard;

    private final ProjectAccessGuard projectAccessGuard;

    private final WorkItemGuard workItemGuard;

    private final WorkItemActivityAppService activityAppService;

    private final PmNotifyPublisher notifyPublisher;

    /**
     * 查询工作项评论（按时间升序）。
     *
     * @param workItemId 工作项ID
     * @param operatorId 当前用户ID
     * @return 评论列表
     */
    public List<WorkItemCommentVO> list(Long workItemId, Long operatorId) {
        workItemGuard.requireReadable(workItemId, operatorId);
        return commentMapper.selectList(new LambdaQueryWrapper<PmComment>()
                .eq(PmComment::getWorkItemId, workItemId)
                .orderByAsc(PmComment::getCreateTime)
                .orderByAsc(PmComment::getId)).stream()
                .map(PmAssembler::toCommentVo)
                .toList();
    }

    /**
     * 新增工作项评论。
     *
     * @param workItemId 工作项ID
     * @param request    新增请求
     * @param operatorId 当前用户ID
     * @return 新增后的评论
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkItemCommentVO add(Long workItemId, WorkItemCommentSaveRequest request, Long operatorId) {
        PmWorkItem item = workItemGuard.requireWritable(workItemId, operatorId);
        projectGuard.requireActiveProject(item.getProjectId());
        PmComment parent = requireParent(request.getParentId(), workItemId);
        PmComment comment = new PmComment();
        comment.setProjectId(item.getProjectId());
        comment.setWorkItemId(workItemId);
        comment.setAuthorId(operatorId);
        comment.setContent(request.getContent().trim());
        comment.setParentId(request.getParentId());
        commentMapper.insert(comment);
        activityAppService.record(item.getProjectId(), workItemId, operatorId, ActivityAction.COMMENT,
                "content", null, comment.getContent());
        notifyComment(item, operatorId, parent, comment.getContent());
        log.info("工作项评论已新增: itemId={}, commentId={}", workItemId, comment.getId());
        return PmAssembler.toCommentVo(comment);
    }

    /**
     * 删除工作项评论（作者本人或项目管理员）。
     *
     * @param commentId  评论ID
     * @param operatorId 当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long commentId, Long operatorId) {
        PmComment comment = commentId == null ? null : commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        PmWorkItem item = workItemGuard.requireReadable(comment.getWorkItemId(), operatorId);
        projectGuard.requireActiveProject(item.getProjectId());
        if (!operatorId.equals(comment.getAuthorId())) {
            projectAccessGuard.requireAdmin(item.getProjectId(), operatorId);
        }
        commentMapper.delete(new LambdaQueryWrapper<PmComment>().eq(PmComment::getParentId, commentId));
        commentMapper.deleteById(commentId);
        activityAppService.record(item.getProjectId(), item.getId(), operatorId, ActivityAction.COMMENT,
                "delete", comment.getContent(), null);
        log.info("工作项评论已删除: itemId={}, commentId={}", item.getId(), commentId);
    }

    /**
     * 校验父评论存在且属于同一工作项。
     *
     * @param parentId   父评论ID，可为 {@code null}
     * @param workItemId 工作项ID
     * @return 父评论，未指定时返回 {@code null}
     */
    private PmComment requireParent(Long parentId, Long workItemId) {
        if (parentId == null) {
            return null;
        }
        PmComment parent = commentMapper.selectById(parentId);
        if (parent == null || !workItemId.equals(parent.getWorkItemId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "父评论不存在或不属于当前工作项");
        }
        return parent;
    }

    /**
     * 发布「被评论」通知：接收人为负责人与被回复人（发布器过滤本人）。
     *
     * @param item       工作项
     * @param operatorId 操作人用户ID
     * @param parent     父评论，可为 {@code null}
     * @param content    评论内容
     */
    private void notifyComment(PmWorkItem item, Long operatorId, PmComment parent, String content) {
        notifyPublisher.publish(PmNotifyEvent.builder()
                .projectId(item.getProjectId())
                .workItemId(item.getId())
                .itemKey(item.getItemKey())
                .itemTitle(item.getTitle())
                .kind(PmNotifyKind.COMMENT)
                .operatorId(operatorId)
                .recipientIds(PmNotifyEvent.recipients(item.getAssigneeId(),
                        parent == null ? null : parent.getAuthorId()))
                .detail(summary(content))
                .build());
    }

    /**
     * 截断评论摘要，避免通知正文过长。
     *
     * @param content 评论内容
     * @return 摘要
     */
    private String summary(String content) {
        if (content == null) {
            return "";
        }
        String text = content.trim();
        return text.length() <= MAX_DETAIL_LENGTH ? text : text.substring(0, MAX_DETAIL_LENGTH) + "…";
    }
}
