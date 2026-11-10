package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pm.application.assembler.PmAssembler;
import com.bluewhale.pm.domain.model.StatusCategory;
import com.bluewhale.pm.domain.service.DefaultWorkflowTemplate;
import com.bluewhale.pm.infrastructure.entity.PmStatus;
import com.bluewhale.pm.infrastructure.entity.PmTransition;
import com.bluewhale.pm.infrastructure.mapper.PmStatusMapper;
import com.bluewhale.pm.infrastructure.mapper.PmTransitionMapper;
import com.bluewhale.pm.interfaces.dto.StatusSaveRequest;
import com.bluewhale.pm.interfaces.dto.TransitionSaveRequest;
import com.bluewhale.pm.interfaces.vo.StatusVO;
import com.bluewhale.pm.interfaces.vo.TransitionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作项状态与流转配置应用服务。
 *
 * <p>新建项目时复制默认模板为项目私有配置；状态编码项目内唯一，流转边禁止自环与重复。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectWorkflowAppService {

    /** 是否标记：是 */
    private static final int FLAG_TRUE = 1;

    /** 是否标记：否 */
    private static final int FLAG_FALSE = 0;

    private final ProjectGuard projectGuard;

    private final PmStatusMapper statusMapper;

    private final PmTransitionMapper transitionMapper;

    /**
     * 为新项目初始化默认状态与流转（已存在配置时直接返回）。
     *
     * @param projectId 项目ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void initializeDefaults(Long projectId) {
        long existing = statusMapper.selectCount(new LambdaQueryWrapper<PmStatus>()
                .eq(PmStatus::getProjectId, projectId));
        if (existing > 0) {
            return;
        }
        Map<String, Long> statusIdByCode = new HashMap<>();
        for (DefaultWorkflowTemplate.StatusDefinition definition : DefaultWorkflowTemplate.statuses()) {
            PmStatus status = new PmStatus();
            status.setProjectId(projectId);
            status.setCode(definition.code());
            status.setName(definition.name());
            status.setCategory(definition.category());
            status.setSortNo(definition.sortNo());
            status.setIsDefault(definition.isDefault() ? FLAG_TRUE : FLAG_FALSE);
            statusMapper.insert(status);
            statusIdByCode.put(definition.code(), status.getId());
        }
        for (DefaultWorkflowTemplate.TransitionDefinition definition : DefaultWorkflowTemplate.transitions()) {
            PmTransition transition = new PmTransition();
            transition.setProjectId(projectId);
            transition.setFromStatusId(statusIdByCode.get(definition.fromCode()));
            transition.setToStatusId(statusIdByCode.get(definition.toCode()));
            transition.setName(definition.name());
            transition.setRequireComment(FLAG_FALSE);
            transitionMapper.insert(transition);
        }
        log.info("项目默认状态与流转已初始化: projectId={}, statusCount={}, transitionCount={}",
                projectId, statusIdByCode.size(), DefaultWorkflowTemplate.transitions().size());
    }

    /**
     * 查询项目状态列表（按排序号升序）。
     *
     * @param projectId 项目ID
     * @return 状态列表
     */
    public List<StatusVO> listStatuses(Long projectId) {
        projectGuard.requireProject(projectId);
        List<PmStatus> statuses = statusMapper.selectList(new LambdaQueryWrapper<PmStatus>()
                .eq(PmStatus::getProjectId, projectId)
                .orderByAsc(PmStatus::getSortNo)
                .orderByAsc(PmStatus::getId));
        return statuses.stream().map(PmAssembler::toStatusVo).toList();
    }

    /**
     * 新增项目状态。
     *
     * @param projectId 项目ID
     * @param request   新增请求
     * @return 新增后的状态
     */
    @Transactional(rollbackFor = Exception.class)
    public StatusVO addStatus(Long projectId, StatusSaveRequest request) {
        projectGuard.requireActiveProject(projectId);
        String code = request.getCode().trim();
        StatusCategory.from(request.getCategory());
        assertCodeUnique(projectId, code);
        boolean isDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (isDefault) {
            clearDefaultStatus(projectId, null);
        }
        PmStatus status = new PmStatus();
        status.setProjectId(projectId);
        status.setCode(code);
        status.setName(request.getName().trim());
        status.setCategory(request.getCategory());
        status.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        status.setIsDefault(isDefault ? FLAG_TRUE : FLAG_FALSE);
        statusMapper.insert(status);
        log.info("项目状态已创建: projectId={}, statusId={}, code={}", projectId, status.getId(), code);
        return PmAssembler.toStatusVo(status);
    }

    /**
     * 更新项目状态（状态编码不可修改）。
     *
     * @param statusId 状态ID
     * @param request  更新请求
     * @return 更新后的状态
     */
    @Transactional(rollbackFor = Exception.class)
    public StatusVO updateStatus(Long statusId, StatusSaveRequest request) {
        PmStatus status = requireStatus(statusId);
        projectGuard.requireActiveProject(status.getProjectId());
        StatusCategory.from(request.getCategory());
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefaultStatus(status.getProjectId(), statusId);
            status.setIsDefault(FLAG_TRUE);
        } else if (Boolean.FALSE.equals(request.getIsDefault())) {
            status.setIsDefault(FLAG_FALSE);
        }
        status.setName(request.getName().trim());
        status.setCategory(request.getCategory());
        if (request.getSortNo() != null) {
            status.setSortNo(request.getSortNo());
        }
        statusMapper.updateById(status);
        log.info("项目状态已更新: statusId={}, code={}", statusId, status.getCode());
        return PmAssembler.toStatusVo(status);
    }

    /**
     * 删除项目状态；默认状态或仍被流转引用的状态拒绝删除。
     *
     * @param statusId 状态ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteStatus(Long statusId) {
        PmStatus status = requireStatus(statusId);
        projectGuard.requireActiveProject(status.getProjectId());
        if (Integer.valueOf(FLAG_TRUE).equals(status.getIsDefault())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "默认状态不可删除，请先指定其他默认状态");
        }
        long referenced = transitionMapper.selectCount(new LambdaQueryWrapper<PmTransition>()
                .eq(PmTransition::getProjectId, status.getProjectId())
                .and(w -> w.eq(PmTransition::getFromStatusId, statusId)
                        .or().eq(PmTransition::getToStatusId, statusId)));
        if (referenced > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态仍被流转配置引用，请先删除相关流转");
        }
        statusMapper.deleteById(statusId);
        log.info("项目状态已删除: statusId={}", statusId);
    }

    /**
     * 查询项目流转配置列表。
     *
     * @param projectId 项目ID
     * @return 流转列表
     */
    public List<TransitionVO> listTransitions(Long projectId) {
        projectGuard.requireProject(projectId);
        List<PmTransition> transitions = transitionMapper.selectList(new LambdaQueryWrapper<PmTransition>()
                .eq(PmTransition::getProjectId, projectId)
                .orderByAsc(PmTransition::getId));
        return transitions.stream().map(PmAssembler::toTransitionVo).toList();
    }

    /**
     * 新增状态流转边。
     *
     * @param projectId 项目ID
     * @param request   新增请求
     * @return 新增后的流转
     */
    @Transactional(rollbackFor = Exception.class)
    public TransitionVO addTransition(Long projectId, TransitionSaveRequest request) {
        projectGuard.requireActiveProject(projectId);
        PmStatus from = requireStatusInProject(request.getFromStatusId(), projectId);
        PmStatus to = requireStatusInProject(request.getToStatusId(), projectId);
        if (from.getId().equals(to.getId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "源状态与目标状态不能相同");
        }
        long duplicated = transitionMapper.selectCount(new LambdaQueryWrapper<PmTransition>()
                .eq(PmTransition::getProjectId, projectId)
                .eq(PmTransition::getFromStatusId, from.getId())
                .eq(PmTransition::getToStatusId, to.getId()));
        if (duplicated > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该状态流转已存在");
        }
        PmTransition transition = new PmTransition();
        transition.setProjectId(projectId);
        transition.setFromStatusId(from.getId());
        transition.setToStatusId(to.getId());
        transition.setName(request.getName().trim());
        transition.setRequireComment(Boolean.TRUE.equals(request.getRequireComment()) ? FLAG_TRUE : FLAG_FALSE);
        transition.setRequirePermission(request.getRequirePermission());
        transitionMapper.insert(transition);
        log.info("状态流转已创建: projectId={}, from={}, to={}", projectId, from.getCode(), to.getCode());
        return PmAssembler.toTransitionVo(transition);
    }

    /**
     * 删除状态流转配置。
     *
     * @param transitionId 流转ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTransition(Long transitionId) {
        PmTransition transition = transitionId == null ? null : transitionMapper.selectById(transitionId);
        if (transition == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "状态流转不存在");
        }
        projectGuard.requireActiveProject(transition.getProjectId());
        transitionMapper.deleteById(transitionId);
        log.info("状态流转已删除: transitionId={}", transitionId);
    }

    /**
     * 按 ID 查询状态，不存在时抛出 404。
     *
     * @param statusId 状态ID
     * @return 状态实体
     */
    private PmStatus requireStatus(Long statusId) {
        PmStatus status = statusId == null ? null : statusMapper.selectById(statusId);
        if (status == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目状态不存在");
        }
        return status;
    }

    /**
     * 按 ID 查询状态并校验归属指定项目。
     *
     * @param statusId  状态ID
     * @param projectId 项目ID
     * @return 状态实体
     */
    private PmStatus requireStatusInProject(Long statusId, Long projectId) {
        PmStatus status = requireStatus(statusId);
        if (!projectId.equals(status.getProjectId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态不属于当前项目");
        }
        return status;
    }

    /**
     * 校验状态编码在项目内唯一。
     *
     * @param projectId 项目ID
     * @param code      状态编码
     */
    private void assertCodeUnique(Long projectId, String code) {
        long count = statusMapper.selectCount(new LambdaQueryWrapper<PmStatus>()
                .eq(PmStatus::getProjectId, projectId)
                .eq(PmStatus::getCode, code));
        if (count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态编码已存在");
        }
    }

    /**
     * 清除项目内其他状态的默认标记。
     *
     * @param projectId      项目ID
     * @param excludeStatusId 排除的状态ID，新增时传 {@code null}
     */
    private void clearDefaultStatus(Long projectId, Long excludeStatusId) {
        LambdaQueryWrapper<PmStatus> wrapper = new LambdaQueryWrapper<PmStatus>()
                .eq(PmStatus::getProjectId, projectId)
                .eq(PmStatus::getIsDefault, FLAG_TRUE);
        if (excludeStatusId != null) {
            wrapper.ne(PmStatus::getId, excludeStatusId);
        }
        List<PmStatus> defaults = statusMapper.selectList(wrapper);
        for (PmStatus item : defaults) {
            item.setIsDefault(FLAG_FALSE);
            statusMapper.updateById(item);
        }
        if (!defaults.isEmpty()) {
            log.info("默认状态标记已转移: projectId={}, cleared={}", projectId, defaults.size());
        }
    }
}
