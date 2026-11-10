package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pm.application.assembler.PmAssembler;
import com.bluewhale.pm.domain.model.ProjectRole;
import com.bluewhale.pm.domain.service.ProjectKeyPolicy;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmProjectMember;
import com.bluewhale.pm.infrastructure.entity.PmStatus;
import com.bluewhale.pm.infrastructure.entity.PmTransition;
import com.bluewhale.pm.infrastructure.mapper.PmProjectMapper;
import com.bluewhale.pm.infrastructure.mapper.PmProjectMemberMapper;
import com.bluewhale.pm.infrastructure.mapper.PmStatusMapper;
import com.bluewhale.pm.infrastructure.mapper.PmTransitionMapper;
import com.bluewhale.pm.interfaces.dto.ProjectQueryRequest;
import com.bluewhale.pm.interfaces.dto.ProjectSaveRequest;
import com.bluewhale.pm.interfaces.dto.ProjectStatusChangeRequest;
import com.bluewhale.pm.interfaces.vo.ProjectVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 项目空间应用服务：分页、详情、创建、更新、归档与删除。
 *
 * <p>创建项目时同步初始化默认状态/流转，并把负责人登记为项目管理员。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectAppService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认可见性 */
    private static final String DEFAULT_VISIBILITY = "PRIVATE";

    private final PmProjectMapper projectMapper;

    private final PmProjectMemberMapper memberMapper;

    private final PmStatusMapper statusMapper;

    private final PmTransitionMapper transitionMapper;

    private final ProjectGuard projectGuard;

    private final ProjectWorkflowAppService workflowAppService;

    /**
     * 分页查询项目。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    public PageResult<ProjectVO> page(ProjectQueryRequest request) {
        LambdaQueryWrapper<PmProject> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            String keyKeyword = keyword.toUpperCase(Locale.ROOT);
            wrapper.and(w -> w.like(PmProject::getName, keyword)
                    .or().like(PmProject::getProjectKey, keyKeyword));
        }
        wrapper.eq(StringUtils.hasText(request.getStatus()), PmProject::getStatus, request.getStatus());
        wrapper.eq(StringUtils.hasText(request.getProjectType()), PmProject::getProjectType,
                request.getProjectType());
        wrapper.orderByDesc(PmProject::getCreateTime);
        long current = Math.max(request.getCurrent(), 1L);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PmProject> page = projectMapper.selectPage(new Page<>(current, size), wrapper);
        List<ProjectVO> records = page.getRecords().stream().map(PmAssembler::toProjectVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 查询项目详情。
     *
     * @param projectId 项目ID
     * @return 项目详情
     */
    public ProjectVO getDetail(Long projectId) {
        return PmAssembler.toProjectVo(projectGuard.requireProject(projectId));
    }

    /**
     * 创建项目，并初始化默认状态/流转与负责人成员。
     *
     * @param request 创建请求
     * @return 创建后的项目
     */
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO create(ProjectSaveRequest request) {
        String key = ProjectKeyPolicy.normalize(request.getKey());
        assertKeyUnique(key, null);
        PmProject project = new PmProject();
        applyRequest(project, request, key);
        project.setStatus(ProjectGuard.STATUS_ACTIVE);
        projectMapper.insert(project);
        workflowAppService.initializeDefaults(project.getId());
        if (project.getLeadUserId() != null) {
            ensureAdminMember(project.getId(), project.getLeadUserId());
        }
        log.info("项目已创建: projectId={}, key={}", project.getId(), key);
        return PmAssembler.toProjectVo(project);
    }

    /**
     * 更新项目基础信息（已归档项目拒绝更新）。
     *
     * @param projectId 项目ID
     * @param request   更新请求
     * @return 更新后的项目
     */
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO update(Long projectId, ProjectSaveRequest request) {
        PmProject project = projectGuard.requireActiveProject(projectId);
        String key = ProjectKeyPolicy.normalize(request.getKey());
        assertKeyUnique(key, projectId);
        applyRequest(project, request, key);
        projectMapper.updateById(project);
        if (project.getLeadUserId() != null) {
            ensureAdminMember(projectId, project.getLeadUserId());
        }
        log.info("项目已更新: projectId={}, key={}", projectId, key);
        return PmAssembler.toProjectVo(project);
    }

    /**
     * 变更项目状态（进行中 / 已归档）。
     *
     * @param projectId 项目ID
     * @param request   状态变更请求
     * @return 变更后的项目
     */
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO changeStatus(Long projectId, ProjectStatusChangeRequest request) {
        PmProject project = projectGuard.requireProject(projectId);
        project.setStatus(request.getStatus());
        projectMapper.updateById(project);
        log.info("项目状态已变更: projectId={}, status={}", projectId, request.getStatus());
        return PmAssembler.toProjectVo(project);
    }

    /**
     * 删除项目并级联逻辑删除成员、状态与流转配置。
     *
     * @param projectId 项目ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long projectId) {
        projectGuard.requireProject(projectId);
        memberMapper.delete(new LambdaQueryWrapper<PmProjectMember>()
                .eq(PmProjectMember::getProjectId, projectId));
        transitionMapper.delete(new LambdaQueryWrapper<PmTransition>()
                .eq(PmTransition::getProjectId, projectId));
        statusMapper.delete(new LambdaQueryWrapper<PmStatus>()
                .eq(PmStatus::getProjectId, projectId));
        projectMapper.deleteById(projectId);
        log.info("项目已删除: projectId={}", projectId);
    }

    /**
     * 将请求字段写入实体。
     *
     * @param project 目标项目实体
     * @param request 请求
     * @param key     归一化后的项目键
     */
    private void applyRequest(PmProject project, ProjectSaveRequest request, String key) {
        project.setProjectKey(key);
        project.setName(request.getName().trim());
        project.setProjectType(request.getProjectType());
        project.setVisibility(StringUtils.hasText(request.getVisibility())
                ? request.getVisibility() : DEFAULT_VISIBILITY);
        project.setLeadUserId(request.getLeadUserId());
        project.setDescription(request.getDescription());
    }

    /**
     * 校验项目键在租户内唯一，更新时排除自身。
     *
     * @param key       项目键
     * @param excludeId 需要排除的项目ID，创建时传 {@code null}
     */
    private void assertKeyUnique(String key, Long excludeId) {
        LambdaQueryWrapper<PmProject> wrapper = new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getProjectKey, key);
        if (excludeId != null) {
            wrapper.ne(PmProject::getId, excludeId);
        }
        if (projectMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "项目键已被占用");
        }
    }

    /**
     * 确保负责人在项目成员中且角色为管理员。
     *
     * @param projectId 项目ID
     * @param userId    负责人用户ID
     */
    private void ensureAdminMember(Long projectId, Long userId) {
        PmProjectMember member = memberMapper.selectOne(new LambdaQueryWrapper<PmProjectMember>()
                .eq(PmProjectMember::getProjectId, projectId)
                .eq(PmProjectMember::getUserId, userId));
        if (member == null) {
            PmProjectMember created = new PmProjectMember();
            created.setProjectId(projectId);
            created.setUserId(userId);
            created.setProjectRole(ProjectRole.ADMIN.name());
            created.setJoinedTime(LocalDateTime.now());
            memberMapper.insert(created);
            return;
        }
        if (!ProjectRole.ADMIN.name().equals(member.getProjectRole())) {
            member.setProjectRole(ProjectRole.ADMIN.name());
            memberMapper.updateById(member);
        }
    }
}
