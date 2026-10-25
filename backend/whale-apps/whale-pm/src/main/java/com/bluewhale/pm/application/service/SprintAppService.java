package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pm.application.assembler.PmAssembler;
import com.bluewhale.pm.domain.model.ActivityAction;
import com.bluewhale.pm.domain.model.SprintStatus;
import com.bluewhale.pm.infrastructure.entity.PmSprint;
import com.bluewhale.pm.infrastructure.entity.PmStatus;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.mapper.PmSprintMapper;
import com.bluewhale.pm.infrastructure.mapper.PmStatusMapper;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemMapper;
import com.bluewhale.pm.interfaces.dto.SprintItemRequest;
import com.bluewhale.pm.interfaces.dto.SprintQueryRequest;
import com.bluewhale.pm.interfaces.dto.SprintSaveRequest;
import com.bluewhale.pm.interfaces.vo.SprintVO;
import com.bluewhale.pm.interfaces.vo.WorkItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 迭代应用服务：生命周期（未启动 / 进行中 / 已关闭）与工作项入迭代。
 *
 * <p>同一项目同时最多 1 个 {@code ACTIVE} 迭代；已关闭迭代不可再加入工作项。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SprintAppService {

    /** 分页单页上限 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 字段名：迭代 */
    private static final String FIELD_SPRINT = "sprint";

    private final PmSprintMapper sprintMapper;

    private final PmWorkItemMapper workItemMapper;

    private final PmStatusMapper statusMapper;

    private final ProjectGuard projectGuard;

    private final ProjectAccessGuard projectAccessGuard;

    private final WorkItemGuard workItemGuard;

    private final WorkItemActivityAppService activityAppService;

    /**
     * 分页查询项目迭代。
     *
     * @param request    查询条件
     * @param operatorId 当前用户ID
     * @return 分页结果
     */
    public PageResult<SprintVO> page(SprintQueryRequest request, Long operatorId) {
        projectAccessGuard.requireReadable(request.getProjectId(), operatorId);
        LambdaQueryWrapper<PmSprint> wrapper = new LambdaQueryWrapper<PmSprint>()
                .eq(PmSprint::getProjectId, request.getProjectId())
                .eq(StringUtils.hasText(request.getStatus()), PmSprint::getStatus, request.getStatus())
                .orderByDesc(PmSprint::getCreateTime)
                .orderByDesc(PmSprint::getId);
        long current = Math.max(request.getCurrent(), 1L);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PmSprint> page = sprintMapper.selectPage(new Page<>(current, size), wrapper);
        List<SprintVO> records = page.getRecords().stream().map(PmAssembler::toSprintVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 查询迭代详情。
     *
     * @param id         迭代ID
     * @param operatorId 当前用户ID
     * @return 迭代详情
     */
    public SprintVO getDetail(Long id, Long operatorId) {
        PmSprint sprint = requireSprint(id);
        projectAccessGuard.requireReadable(sprint.getProjectId(), operatorId);
        return PmAssembler.toSprintVo(sprint);
    }

    /**
     * 创建迭代（初始为 FUTURE）。
     *
     * @param request    创建请求
     * @param operatorId 当前用户ID
     * @return 创建后的迭代
     */
    @Transactional(rollbackFor = Exception.class)
    public SprintVO create(SprintSaveRequest request, Long operatorId) {
        projectAccessGuard.requireWritable(request.getProjectId(), operatorId);
        projectGuard.requireActiveProject(request.getProjectId());
        validateDateRange(request);
        String name = request.getName().trim();
        assertNameUnique(request.getProjectId(), name, null);
        PmSprint sprint = new PmSprint();
        sprint.setProjectId(request.getProjectId());
        sprint.setName(name);
        sprint.setGoal(request.getGoal());
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());
        sprint.setStatus(SprintStatus.FUTURE.name());
        sprintMapper.insert(sprint);
        log.info("迭代已创建: projectId={}, sprintId={}, name={}", sprint.getProjectId(), sprint.getId(), name);
        return PmAssembler.toSprintVo(sprint);
    }

    /**
     * 更新迭代（仅未启动迭代可编辑）。
     *
     * @param id         迭代ID
     * @param request    更新请求
     * @param operatorId 当前用户ID
     * @return 更新后的迭代
     */
    @Transactional(rollbackFor = Exception.class)
    public SprintVO update(Long id, SprintSaveRequest request, Long operatorId) {
        PmSprint sprint = requireSprint(id);
        projectAccessGuard.requireWritable(sprint.getProjectId(), operatorId);
        projectGuard.requireActiveProject(sprint.getProjectId());
        requireStatus(sprint, SprintStatus.FUTURE, "仅未启动迭代可编辑");
        validateDateRange(request);
        String name = request.getName().trim();
        assertNameUnique(sprint.getProjectId(), name, id);
        sprint.setName(name);
        sprint.setGoal(request.getGoal());
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());
        sprintMapper.updateById(sprint);
        return PmAssembler.toSprintVo(sprint);
    }

    /**
     * 删除迭代（仅未启动且无工作项）。
     *
     * @param id         迭代ID
     * @param operatorId 当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long operatorId) {
        PmSprint sprint = requireSprint(id);
        projectAccessGuard.requireWritable(sprint.getProjectId(), operatorId);
        projectGuard.requireActiveProject(sprint.getProjectId());
        requireStatus(sprint, SprintStatus.FUTURE, "仅未启动迭代可删除");
        Long items = workItemMapper.selectCount(new LambdaQueryWrapper<PmWorkItem>()
                .eq(PmWorkItem::getSprintId, id));
        if (items != null && items > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "迭代下仍有工作项，请先移出后再删除");
        }
        sprintMapper.deleteById(id);
        log.info("迭代已删除: sprintId={}", id);
    }

    /**
     * 启动迭代（FUTURE → ACTIVE，同项目仅允许 1 个进行中）。
     *
     * @param id         迭代ID
     * @param operatorId 当前用户ID
     * @return 启动后的迭代
     */
    @Transactional(rollbackFor = Exception.class)
    public SprintVO start(Long id, Long operatorId) {
        PmSprint sprint = requireSprint(id);
        projectAccessGuard.requireWritable(sprint.getProjectId(), operatorId);
        projectGuard.requireActiveProject(sprint.getProjectId());
        requireStatus(sprint, SprintStatus.FUTURE, "仅未启动迭代可启动");
        if (sprint.getStartDate() == null || sprint.getEndDate() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "启动迭代前需填写开始与结束日期");
        }
        Long active = sprintMapper.selectCount(new LambdaQueryWrapper<PmSprint>()
                .eq(PmSprint::getProjectId, sprint.getProjectId())
                .eq(PmSprint::getStatus, SprintStatus.ACTIVE.name()));
        if (active != null && active > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "同一项目同时仅允许 1 个进行中的迭代");
        }
        sprint.setStatus(SprintStatus.ACTIVE.name());
        sprintMapper.updateById(sprint);
        return PmAssembler.toSprintVo(sprint);
    }

    /**
     * 关闭迭代（ACTIVE → CLOSED）。
     *
     * @param id         迭代ID
     * @param operatorId 当前用户ID
     * @return 关闭后的迭代
     */
    @Transactional(rollbackFor = Exception.class)
    public SprintVO close(Long id, Long operatorId) {
        PmSprint sprint = requireSprint(id);
        projectAccessGuard.requireWritable(sprint.getProjectId(), operatorId);
        projectGuard.requireActiveProject(sprint.getProjectId());
        requireStatus(sprint, SprintStatus.ACTIVE, "仅进行中迭代可关闭");
        sprint.setStatus(SprintStatus.CLOSED.name());
        sprintMapper.updateById(sprint);
        return PmAssembler.toSprintVo(sprint);
    }

    /**
     * 查询迭代内工作项（按排序值升序）。
     *
     * @param id         迭代ID
     * @param operatorId 当前用户ID
     * @return 工作项列表
     */
    public List<WorkItemVO> listItems(Long id, Long operatorId) {
        PmSprint sprint = requireSprint(id);
        projectAccessGuard.requireReadable(sprint.getProjectId(), operatorId);
        List<PmWorkItem> items = workItemMapper.selectList(new LambdaQueryWrapper<PmWorkItem>()
                .eq(PmWorkItem::getSprintId, id)
                .orderByAsc(PmWorkItem::getRankValue)
                .orderByAsc(PmWorkItem::getId));
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> statusIds = items.stream().map(PmWorkItem::getStatusId).distinct().toList();
        Map<Long, PmStatus> statuses = statusMapper.selectBatchIds(statusIds).stream()
                .collect(Collectors.toMap(PmStatus::getId, Function.identity()));
        return items.stream()
                .map(item -> PmAssembler.toWorkItemVo(item, statuses.get(item.getStatusId())))
                .toList();
    }

    /**
     * 批量将工作项加入迭代（同项目、未关闭）。
     *
     * @param id         迭代ID
     * @param request    工作项ID列表
     * @param operatorId 当前用户ID
     * @return 成功加入数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int addItems(Long id, SprintItemRequest request, Long operatorId) {
        PmSprint sprint = requireSprint(id);
        projectAccessGuard.requireWritable(sprint.getProjectId(), operatorId);
        projectGuard.requireActiveProject(sprint.getProjectId());
        if (SprintStatus.CLOSED.name().equals(sprint.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "迭代已关闭，不可再加入工作项");
        }
        int added = 0;
        for (Long itemId : request.getItemIds()) {
            PmWorkItem item = workItemGuard.requireInProject(itemId, sprint.getProjectId());
            if (id.equals(item.getSprintId())) {
                continue;
            }
            int affected = workItemMapper.update(null, new LambdaUpdateWrapper<PmWorkItem>()
                    .eq(PmWorkItem::getId, item.getId())
                    .eq(PmWorkItem::getVersion, item.getVersion())
                    .set(PmWorkItem::getSprintId, id)
                    .set(PmWorkItem::getVersion, item.getVersion() + 1));
            if (affected == 0) {
                throw new BizException(ErrorCode.REPEAT_SUBMIT, "工作项已被他人修改，请刷新后重试");
            }
            activityAppService.record(sprint.getProjectId(), item.getId(), operatorId, ActivityAction.UPDATE,
                    FIELD_SPRINT, null, sprint.getName());
            added++;
        }
        return added;
    }

    /**
     * 将工作项移出迭代。
     *
     * @param id         迭代ID
     * @param itemId     工作项ID
     * @param operatorId 当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeItem(Long id, Long itemId, Long operatorId) {
        PmSprint sprint = requireSprint(id);
        projectAccessGuard.requireWritable(sprint.getProjectId(), operatorId);
        projectGuard.requireActiveProject(sprint.getProjectId());
        if (SprintStatus.CLOSED.name().equals(sprint.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "迭代已关闭，不可调整工作项");
        }
        PmWorkItem item = workItemGuard.requireInProject(itemId, sprint.getProjectId());
        if (!id.equals(item.getSprintId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工作项不属于当前迭代");
        }
        int affected = workItemMapper.update(null, new LambdaUpdateWrapper<PmWorkItem>()
                .eq(PmWorkItem::getId, item.getId())
                .eq(PmWorkItem::getVersion, item.getVersion())
                .set(PmWorkItem::getSprintId, null)
                .set(PmWorkItem::getVersion, item.getVersion() + 1));
        if (affected == 0) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT, "工作项已被他人修改，请刷新后重试");
        }
        activityAppService.record(sprint.getProjectId(), item.getId(), operatorId, ActivityAction.UPDATE,
                FIELD_SPRINT, sprint.getName(), null);
    }

    /**
     * 查询迭代并要求存在。
     *
     * @param id 迭代ID
     * @return 迭代实体
     */
    private PmSprint requireSprint(Long id) {
        PmSprint sprint = id == null ? null : sprintMapper.selectById(id);
        if (sprint == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "迭代不存在");
        }
        return sprint;
    }

    /**
     * 校验迭代状态。
     *
     * @param sprint 迭代实体
     * @param target 期望状态
     * @param message 不满足时的提示
     */
    private void requireStatus(PmSprint sprint, SprintStatus target, String message) {
        if (!target.name().equals(sprint.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, message);
        }
    }

    /**
     * 校验名称在项目内唯一。
     *
     * @param projectId 项目ID
     * @param name      迭代名称
     * @param excludeId 需排除的迭代ID，可为 {@code null}
     */
    private void assertNameUnique(Long projectId, String name, Long excludeId) {
        Long count = sprintMapper.selectCount(new LambdaQueryWrapper<PmSprint>()
                .eq(PmSprint::getProjectId, projectId)
                .eq(PmSprint::getName, name)
                .ne(excludeId != null, PmSprint::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "迭代名称已存在");
        }
    }

    /**
     * 校验起止日期区间。
     *
     * @param request 迭代请求
     */
    private void validateDateRange(SprintSaveRequest request) {
        if (request.getStartDate() != null && request.getEndDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "结束日期不能早于开始日期");
        }
    }
}
