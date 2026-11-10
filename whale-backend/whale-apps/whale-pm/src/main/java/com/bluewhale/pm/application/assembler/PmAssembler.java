package com.bluewhale.pm.application.assembler;

import com.bluewhale.pm.infrastructure.entity.PmActivity;
import com.bluewhale.pm.infrastructure.entity.PmAttachment;
import com.bluewhale.pm.infrastructure.entity.PmBoard;
import com.bluewhale.pm.infrastructure.entity.PmBoardColumn;
import com.bluewhale.pm.infrastructure.entity.PmComment;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmProjectMember;
import com.bluewhale.pm.infrastructure.entity.PmSprint;
import com.bluewhale.pm.infrastructure.entity.PmStatus;
import com.bluewhale.pm.infrastructure.entity.PmTransition;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.entity.PmWorkItemLink;
import com.bluewhale.pm.interfaces.vo.ActivityVO;
import com.bluewhale.pm.interfaces.vo.BoardColumnVO;
import com.bluewhale.pm.interfaces.vo.BoardColumnViewVO;
import com.bluewhale.pm.interfaces.vo.BoardVO;
import com.bluewhale.pm.interfaces.vo.ProjectMemberVO;
import com.bluewhale.pm.interfaces.vo.ProjectVO;
import com.bluewhale.pm.interfaces.vo.SprintVO;
import com.bluewhale.pm.interfaces.vo.StatusVO;
import com.bluewhale.pm.interfaces.vo.TransitionVO;
import com.bluewhale.pm.interfaces.vo.WorkItemAttachmentVO;
import com.bluewhale.pm.interfaces.vo.WorkItemCommentVO;
import com.bluewhale.pm.interfaces.vo.WorkItemLinkVO;
import com.bluewhale.pm.interfaces.vo.WorkItemVO;

import java.util.List;
import java.util.Map;

/**
 * PM 实体与视图对象转换器（ID 一律转为字符串）
 *
 * @author BlueWhale
 */
public final class PmAssembler {

    /** 布尔标记：是 */
    private static final int FLAG_TRUE = 1;

    /**
     * 工具类不允许实例化
     */
    private PmAssembler() {
    }

    /**
     * 项目实体转视图对象
     *
     * @param project 项目实体
     * @return 项目视图对象
     */
    public static ProjectVO toProjectVo(PmProject project) {
        ProjectVO vo = new ProjectVO();
        vo.setId(asString(project.getId()));
        vo.setKey(project.getProjectKey());
        vo.setName(project.getName());
        vo.setProjectType(project.getProjectType());
        vo.setVisibility(project.getVisibility());
        vo.setLeadUserId(asString(project.getLeadUserId()));
        vo.setDescription(project.getDescription());
        vo.setStatus(project.getStatus());
        vo.setCreateTime(project.getCreateTime());
        vo.setUpdateTime(project.getUpdateTime());
        return vo;
    }

    /**
     * 成员实体转视图对象
     *
     * @param member 成员实体
     * @return 成员视图对象
     */
    public static ProjectMemberVO toMemberVo(PmProjectMember member) {
        ProjectMemberVO vo = new ProjectMemberVO();
        vo.setId(asString(member.getId()));
        vo.setProjectId(asString(member.getProjectId()));
        vo.setUserId(asString(member.getUserId()));
        vo.setProjectRole(member.getProjectRole());
        vo.setJoinedTime(member.getJoinedTime());
        vo.setCreateTime(member.getCreateTime());
        return vo;
    }

    /**
     * 状态实体转视图对象
     *
     * @param status 状态实体
     * @return 状态视图对象
     */
    public static StatusVO toStatusVo(PmStatus status) {
        StatusVO vo = new StatusVO();
        vo.setId(asString(status.getId()));
        vo.setProjectId(asString(status.getProjectId()));
        vo.setCode(status.getCode());
        vo.setName(status.getName());
        vo.setCategory(status.getCategory());
        vo.setSortNo(status.getSortNo());
        vo.setIsDefault(Integer.valueOf(FLAG_TRUE).equals(status.getIsDefault()));
        vo.setCreateTime(status.getCreateTime());
        return vo;
    }

    /**
     * 流转实体转视图对象
     *
     * @param transition 流转实体
     * @return 流转视图对象
     */
    public static TransitionVO toTransitionVo(PmTransition transition) {
        TransitionVO vo = new TransitionVO();
        vo.setId(asString(transition.getId()));
        vo.setProjectId(asString(transition.getProjectId()));
        vo.setFromStatusId(asString(transition.getFromStatusId()));
        vo.setToStatusId(asString(transition.getToStatusId()));
        vo.setName(transition.getName());
        vo.setRequireComment(Integer.valueOf(FLAG_TRUE).equals(transition.getRequireComment()));
        vo.setRequirePermission(transition.getRequirePermission());
        vo.setCreateTime(transition.getCreateTime());
        return vo;
    }

    /**
     * 工作项实体转视图对象
     *
     * @param item   工作项实体
     * @param status 工作项状态，可为 {@code null}
     * @return 工作项视图对象
     */
    public static WorkItemVO toWorkItemVo(PmWorkItem item, PmStatus status) {
        WorkItemVO vo = new WorkItemVO();
        vo.setId(asString(item.getId()));
        vo.setProjectId(asString(item.getProjectId()));
        vo.setItemNo(item.getItemNo());
        vo.setKey(item.getItemKey());
        vo.setType(item.getType());
        vo.setTitle(item.getTitle());
        vo.setDescription(item.getDescription());
        vo.setStatusId(asString(item.getStatusId()));
        if (status != null) {
            vo.setStatusCode(status.getCode());
            vo.setStatusName(status.getName());
            vo.setStatusCategory(status.getCategory());
        }
        vo.setPriority(item.getPriority());
        vo.setAssigneeId(asString(item.getAssigneeId()));
        vo.setReporterId(asString(item.getReporterId()));
        vo.setParentId(asString(item.getParentId()));
        vo.setSprintId(asString(item.getSprintId()));
        vo.setStoryPoints(item.getStoryPoints());
        vo.setDueDate(item.getDueDate());
        vo.setLabels(item.getLabels());
        vo.setRankValue(item.getRankValue());
        vo.setExtensionJson(item.getExtensionJson());
        vo.setVersion(item.getVersion());
        vo.setCreateTime(item.getCreateTime());
        vo.setUpdateTime(item.getUpdateTime());
        return vo;
    }

    /**
     * 评论实体转视图对象
     *
     * @param comment 评论实体
     * @return 评论视图对象
     */
    public static WorkItemCommentVO toCommentVo(PmComment comment) {
        WorkItemCommentVO vo = new WorkItemCommentVO();
        vo.setId(asString(comment.getId()));
        vo.setWorkItemId(asString(comment.getWorkItemId()));
        vo.setAuthorId(asString(comment.getAuthorId()));
        vo.setContent(comment.getContent());
        vo.setParentId(asString(comment.getParentId()));
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }

    /**
     * 附件实体转视图对象
     *
     * @param attachment 附件实体
     * @return 附件视图对象
     */
    public static WorkItemAttachmentVO toAttachmentVo(PmAttachment attachment) {
        WorkItemAttachmentVO vo = new WorkItemAttachmentVO();
        vo.setId(asString(attachment.getId()));
        vo.setWorkItemId(asString(attachment.getWorkItemId()));
        vo.setFileId(asString(attachment.getFileId()));
        vo.setFileName(attachment.getFileName());
        vo.setUploaderId(asString(attachment.getUploaderId()));
        vo.setCreateTime(attachment.getCreateTime());
        return vo;
    }

    /**
     * 关联实体转视图对象
     *
     * @param link   关联实体
     * @param target 目标工作项，可为 {@code null}
     * @return 关联视图对象
     */
    public static WorkItemLinkVO toLinkVo(PmWorkItemLink link, PmWorkItem target) {
        WorkItemLinkVO vo = new WorkItemLinkVO();
        vo.setId(asString(link.getId()));
        vo.setSourceId(asString(link.getSourceId()));
        vo.setTargetId(asString(link.getTargetId()));
        if (target != null) {
            vo.setTargetKey(target.getItemKey());
            vo.setTargetTitle(target.getTitle());
        }
        vo.setLinkType(link.getLinkType());
        vo.setCreateTime(link.getCreateTime());
        return vo;
    }

    /**
     * 迭代实体转视图对象
     *
     * @param sprint 迭代实体
     * @return 迭代视图对象
     */
    public static SprintVO toSprintVo(PmSprint sprint) {
        SprintVO vo = new SprintVO();
        vo.setId(asString(sprint.getId()));
        vo.setProjectId(asString(sprint.getProjectId()));
        vo.setName(sprint.getName());
        vo.setGoal(sprint.getGoal());
        vo.setStartDate(sprint.getStartDate());
        vo.setEndDate(sprint.getEndDate());
        vo.setStatus(sprint.getStatus());
        vo.setCreateTime(sprint.getCreateTime());
        vo.setUpdateTime(sprint.getUpdateTime());
        return vo;
    }

    /**
     * 看板实体转视图对象
     *
     * @param board    看板实体
     * @param columns  列配置
     * @param statuses 状态索引（按状态ID）
     * @return 看板视图对象
     */
    public static BoardVO toBoardVo(PmBoard board, List<PmBoardColumn> columns, Map<Long, PmStatus> statuses) {
        BoardVO vo = new BoardVO();
        vo.setId(asString(board.getId()));
        vo.setProjectId(asString(board.getProjectId()));
        vo.setName(board.getName());
        vo.setBoardType(board.getBoardType());
        vo.setColumns(columns.stream()
                .map(column -> toBoardColumnVo(column, statuses.get(column.getStatusId())))
                .toList());
        vo.setCreateTime(board.getCreateTime());
        vo.setUpdateTime(board.getUpdateTime());
        return vo;
    }

    /**
     * 看板列实体转视图对象
     *
     * @param column 看板列实体
     * @param status 映射状态，可为 {@code null}
     * @return 看板列视图对象
     */
    public static BoardColumnVO toBoardColumnVo(PmBoardColumn column, PmStatus status) {
        BoardColumnVO vo = new BoardColumnVO();
        vo.setId(asString(column.getId()));
        vo.setBoardId(asString(column.getBoardId()));
        vo.setStatusId(asString(column.getStatusId()));
        if (status != null) {
            vo.setStatusCode(status.getCode());
        }
        vo.setName(column.getName());
        vo.setSortNo(column.getSortNo());
        vo.setWipLimit(column.getWipLimit());
        return vo;
    }

    /**
     * 看板列转执行视图（含列内工作项与 WIP 状态）
     *
     * @param column 看板列实体
     * @param status 映射状态，可为 {@code null}
     * @param items  列内工作项
     * @return 看板列执行视图
     */
    public static BoardColumnViewVO toBoardColumnViewVo(PmBoardColumn column, PmStatus status,
            List<WorkItemVO> items) {
        BoardColumnViewVO vo = new BoardColumnViewVO();
        vo.setId(asString(column.getId()));
        vo.setStatusId(asString(column.getStatusId()));
        if (status != null) {
            vo.setStatusCode(status.getCode());
        }
        vo.setName(column.getName());
        vo.setSortNo(column.getSortNo());
        vo.setWipLimit(column.getWipLimit());
        vo.setItemCount(items.size());
        int limit = column.getWipLimit() == null ? 0 : column.getWipLimit();
        vo.setOverloaded(limit > 0 && items.size() > limit);
        vo.setItems(items);
        return vo;
    }

    /**
     * 动态实体转视图对象
     *
     * @param activity 动态实体
     * @return 动态视图对象
     */
    public static ActivityVO toActivityVo(PmActivity activity) {
        ActivityVO vo = new ActivityVO();
        vo.setId(asString(activity.getId()));
        vo.setProjectId(asString(activity.getProjectId()));
        vo.setWorkItemId(asString(activity.getWorkItemId()));
        vo.setActorId(asString(activity.getActorId()));
        vo.setAction(activity.getAction());
        vo.setField(activity.getField());
        vo.setOldValue(activity.getOldValue());
        vo.setNewValue(activity.getNewValue());
        vo.setTraceId(activity.getTraceId());
        vo.setCreateTime(activity.getCreateTime());
        return vo;
    }

    /**
     * 长整型转字符串，空值返回 {@code null}
     *
     * @param value 长整型值
     * @return 字符串；入参为空时返回 {@code null}
     */
    private static String asString(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
