package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pm.domain.model.ProjectRole;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmProjectMember;
import com.bluewhale.pm.infrastructure.mapper.PmProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 项目数据范围守卫：在平台功能权限之外，按项目角色收敛读写范围。
 *
 * <p>READ 允许 ADMIN / MEMBER / VIEWER；WRITE 允许 ADMIN / MEMBER；ADMIN 仅管理员。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ProjectAccessGuard {

    private final PmProjectMemberMapper memberMapper;

    private final ProjectGuard projectGuard;

    /**
     * 校验当前用户可读指定项目。
     *
     * @param projectId 项目ID
     * @param userId    当前用户ID
     * @return 项目实体
     */
    public PmProject requireReadable(Long projectId, Long userId) {
        requireMember(projectId, userId);
        return projectGuard.requireProject(projectId);
    }

    /**
     * 校验当前用户可写指定项目（管理员或普通成员）。
     *
     * @param projectId 项目ID
     * @param userId    当前用户ID
     * @return 项目成员
     */
    public PmProjectMember requireWritable(Long projectId, Long userId) {
        PmProjectMember member = requireMember(projectId, userId);
        if (ProjectRole.VIEWER.name().equals(member.getProjectRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只读成员不可执行该操作");
        }
        return member;
    }

    /**
     * 校验当前用户为项目管理员。
     *
     * @param projectId 项目ID
     * @param userId    当前用户ID
     * @return 项目成员
     */
    public PmProjectMember requireAdmin(Long projectId, Long userId) {
        PmProjectMember member = requireMember(projectId, userId);
        if (!ProjectRole.ADMIN.name().equals(member.getProjectRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅项目管理员可执行该操作");
        }
        return member;
    }

    /**
     * 校验用户上下文并查询项目成员关系。
     *
     * @param projectId 项目ID
     * @param userId    当前用户ID
     * @return 项目成员
     */
    private PmProjectMember requireMember(Long projectId, Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少用户上下文");
        }
        PmProjectMember member = memberMapper.selectOne(new LambdaQueryWrapper<PmProjectMember>()
                .eq(PmProjectMember::getProjectId, projectId)
                .eq(PmProjectMember::getUserId, userId));
        if (member == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "非项目成员，无权访问");
        }
        return member;
    }
}
