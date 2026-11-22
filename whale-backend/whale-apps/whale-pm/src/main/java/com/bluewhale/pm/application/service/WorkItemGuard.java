package com.bluewhale.pm.application.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 工作项存在性与数据范围守卫。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class WorkItemGuard {

    private final PmWorkItemMapper workItemMapper;

    private final ProjectAccessGuard projectAccessGuard;

    /**
     * 按 ID 查询工作项，不存在时抛出 404。
     *
     * @param workItemId 工作项ID
     * @return 工作项实体
     */
    public PmWorkItem requireItem(Long workItemId) {
        PmWorkItem item = workItemId == null ? null : workItemMapper.selectById(workItemId);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pm.application.work-item-found", "工作项不存在");
        }
        return item;
    }

    /**
     * 查询工作项并校验只读权限。
     *
     * @param workItemId 工作项ID
     * @param userId     当前用户ID
     * @return 工作项实体
     */
    public PmWorkItem requireReadable(Long workItemId, Long userId) {
        PmWorkItem item = requireItem(workItemId);
        projectAccessGuard.requireReadable(item.getProjectId(), userId);
        return item;
    }

    /**
     * 查询工作项并校验写权限。
     *
     * @param workItemId 工作项ID
     * @param userId     当前用户ID
     * @return 工作项实体
     */
    public PmWorkItem requireWritable(Long workItemId, Long userId) {
        PmWorkItem item = requireItem(workItemId);
        projectAccessGuard.requireWritable(item.getProjectId(), userId);
        return item;
    }

    /**
     * 查询工作项并校验归属项目。
     *
     * @param workItemId 工作项ID
     * @param projectId  项目ID
     * @return 工作项实体
     */
    public PmWorkItem requireInProject(Long workItemId, Long projectId) {
        PmWorkItem item = requireItem(workItemId);
        if (!projectId.equals(item.getProjectId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.application.work-item-belong-project", "工作项不属于当前项目");
        }
        return item;
    }
}
