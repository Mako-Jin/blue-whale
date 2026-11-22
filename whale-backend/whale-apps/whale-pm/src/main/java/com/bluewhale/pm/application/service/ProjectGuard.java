package com.bluewhale.pm.application.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.mapper.PmProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 项目存在性与可用性守卫，供各应用服务复用。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ProjectGuard {

    /** 项目状态：进行中 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 项目状态：已归档 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private final PmProjectMapper projectMapper;

    /**
     * 按 ID 查询未删除的项目，不存在时抛出 404。
     *
     * @param projectId 项目ID
     * @return 项目实体
     */
    public PmProject requireProject(Long projectId) {
        PmProject project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pm.application.project-found", "项目不存在");
        }
        return project;
    }

    /**
     * 查询项目并要求其处于进行中状态，已归档项目拒绝配置变更。
     *
     * @param projectId 项目ID
     * @return 项目实体
     */
    public PmProject requireActiveProject(Long projectId) {
        PmProject project = requireProject(projectId);
        if (STATUS_ARCHIVED.equals(project.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.application.project-archived-restore-it-in-progress", "项目已归档，请先恢复为进行中");
        }
        return project;
    }
}
