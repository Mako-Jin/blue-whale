package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pm.application.assembler.PmAssembler;
import com.bluewhale.pm.domain.model.ProjectRole;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmProjectMember;
import com.bluewhale.pm.infrastructure.mapper.PmProjectMemberMapper;
import com.bluewhale.pm.interfaces.dto.ProjectMemberSaveRequest;
import com.bluewhale.pm.interfaces.dto.ProjectMemberUpdateRequest;
import com.bluewhale.pm.interfaces.vo.ProjectMemberVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目成员应用服务：列表、新增、角色变更与移除。
 *
 * <p>数据范围约束：至少保留一名项目管理员；项目负责人不可被移除或降级。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectMemberAppService {

    private final PmProjectMemberMapper memberMapper;

    private final ProjectGuard projectGuard;

    /**
     * 查询项目成员列表。
     *
     * @param projectId 项目ID
     * @return 成员列表
     */
    public List<ProjectMemberVO> list(Long projectId) {
        projectGuard.requireProject(projectId);
        List<PmProjectMember> members = memberMapper.selectList(new LambdaQueryWrapper<PmProjectMember>()
                .eq(PmProjectMember::getProjectId, projectId)
                .orderByAsc(PmProjectMember::getJoinedTime)
                .orderByAsc(PmProjectMember::getId));
        return members.stream().map(PmAssembler::toMemberVo).toList();
    }

    /**
     * 新增项目成员。
     *
     * @param projectId 项目ID
     * @param request   新增请求
     * @return 新增后的成员
     */
    @Transactional(rollbackFor = Exception.class)
    public ProjectMemberVO add(Long projectId, ProjectMemberSaveRequest request) {
        projectGuard.requireActiveProject(projectId);
        ProjectRole role = ProjectRole.from(request.getProjectRole());
        long duplicated = memberMapper.selectCount(new LambdaQueryWrapper<PmProjectMember>()
                .eq(PmProjectMember::getProjectId, projectId)
                .eq(PmProjectMember::getUserId, request.getUserId()));
        if (duplicated > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.application.already-project-member", "该用户已是项目成员");
        }
        PmProjectMember member = new PmProjectMember();
        member.setProjectId(projectId);
        member.setUserId(request.getUserId());
        member.setProjectRole(role.name());
        member.setJoinedTime(LocalDateTime.now());
        memberMapper.insert(member);
        log.info("项目成员已新增: projectId={}, userId={}, role={}", projectId, request.getUserId(), role);
        return PmAssembler.toMemberVo(member);
    }

    /**
     * 变更项目成员角色。
     *
     * @param memberId 成员记录ID
     * @param request  角色变更请求
     * @return 变更后的成员
     */
    @Transactional(rollbackFor = Exception.class)
    public ProjectMemberVO updateRole(Long memberId, ProjectMemberUpdateRequest request) {
        PmProjectMember member = requireMember(memberId);
        PmProject project = projectGuard.requireActiveProject(member.getProjectId());
        ProjectRole role = ProjectRole.from(request.getProjectRole());
        boolean leader = member.getUserId().equals(project.getLeadUserId());
        if (leader && role != ProjectRole.ADMIN) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.application.project-owner-retain-administrator-role", "项目负责人必须保留管理员角色");
        }
        if (ProjectRole.ADMIN.name().equals(member.getProjectRole()) && role != ProjectRole.ADMIN) {
            assertNotLastAdmin(member);
        }
        member.setProjectRole(role.name());
        memberMapper.updateById(member);
        log.info("项目成员角色已变更: memberId={}, role={}", memberId, role);
        return PmAssembler.toMemberVo(member);
    }

    /**
     * 移除项目成员。
     *
     * @param memberId 成员记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long memberId) {
        PmProjectMember member = requireMember(memberId);
        PmProject project = projectGuard.requireActiveProject(member.getProjectId());
        if (member.getUserId().equals(project.getLeadUserId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.application.project-owner-cannot-removed-change-owner", "项目负责人不可移除，请先变更负责人");
        }
        if (ProjectRole.ADMIN.name().equals(member.getProjectRole())) {
            assertNotLastAdmin(member);
        }
        memberMapper.deleteById(memberId);
        log.info("项目成员已移除: memberId={}, userId={}", memberId, member.getUserId());
    }

    /**
     * 按 ID 查询成员记录，不存在时抛出 404。
     *
     * @param memberId 成员记录ID
     * @return 成员实体
     */
    private PmProjectMember requireMember(Long memberId) {
        PmProjectMember member = memberId == null ? null : memberMapper.selectById(memberId);
        if (member == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pm.application.project-member-found", "项目成员不存在");
        }
        return member;
    }

    /**
     * 校验项目内至少保留一名管理员。
     *
     * @param member 待降级或移除的成员
     */
    private void assertNotLastAdmin(PmProjectMember member) {
        long adminCount = memberMapper.selectCount(new LambdaQueryWrapper<PmProjectMember>()
                .eq(PmProjectMember::getProjectId, member.getProjectId())
                .eq(PmProjectMember::getProjectRole, ProjectRole.ADMIN.name()));
        if (adminCount <= 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.application.project-retain-at-least-one-administrator", "项目至少保留一名管理员");
        }
    }
}
