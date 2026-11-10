package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pm.application.assembler.PmAssembler;
import com.bluewhale.pm.application.notify.PmNotifyEvent;
import com.bluewhale.pm.application.notify.PmNotifyKind;
import com.bluewhale.pm.application.notify.PmNotifyPublisher;
import com.bluewhale.pm.domain.model.ActivityAction;
import com.bluewhale.pm.domain.model.Priority;
import com.bluewhale.pm.domain.model.WorkItemType;
import com.bluewhale.pm.domain.service.RankPolicy;
import com.bluewhale.pm.infrastructure.client.ItemSequenceGenerator;
import com.bluewhale.pm.infrastructure.entity.PmComment;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmStatus;
import com.bluewhale.pm.infrastructure.entity.PmTransition;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.mapper.PmAttachmentMapper;
import com.bluewhale.pm.infrastructure.mapper.PmCommentMapper;
import com.bluewhale.pm.infrastructure.mapper.PmStatusMapper;
import com.bluewhale.pm.infrastructure.mapper.PmTransitionMapper;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemLinkMapper;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemMapper;
import com.bluewhale.pm.interfaces.dto.WorkItemQueryRequest;
import com.bluewhale.pm.interfaces.dto.WorkItemRankRequest;
import com.bluewhale.pm.interfaces.dto.WorkItemSaveRequest;
import com.bluewhale.pm.interfaces.dto.WorkItemTransitionRequest;
import com.bluewhale.pm.interfaces.vo.ActivityVO;
import com.bluewhale.pm.interfaces.vo.WorkItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 工作项应用服务：分页、详情、创建、更新、删除、状态流转与排序。
 *
 * <p>工作项键由「项目键-项目内序号」组成，序号经 Redis 原子自增；排序值使用
 * {@link RankPolicy} 的 12 位定长数字串，空间不足时项目内局部重排。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkItemAppService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认优先级 */
    private static final String DEFAULT_PRIORITY = "MEDIUM";

    /** 初始版本号 */
    private static final int INITIAL_VERSION = 0;

    /** 重排临时排序值前缀 */
    private static final String TEMP_RANK_PREFIX = "TMP";

    /** 是否标记：是 */
    private static final int FLAG_TRUE = 1;

    /** 父链最大深度，防止脏数据导致死循环 */
    private static final int MAX_PARENT_DEPTH = 20;

    private final PmWorkItemMapper workItemMapper;

    private final PmStatusMapper statusMapper;

    private final PmTransitionMapper transitionMapper;

    private final PmCommentMapper commentMapper;

    private final PmAttachmentMapper attachmentMapper;

    private final PmWorkItemLinkMapper linkMapper;

    private final ProjectGuard projectGuard;

    private final ProjectAccessGuard projectAccessGuard;

    private final WorkItemGuard workItemGuard;

    private final ItemSequenceGenerator sequenceGenerator;

    private final WorkItemActivityAppService activityAppService;

    private final PmNotifyPublisher notifyPublisher;

    /**
     * 分页查询工作项（按排序值升序）。
     *
     * @param request    查询条件
     * @param operatorId 当前用户ID
     * @return 分页结果
     */
    public PageResult<WorkItemVO> page(WorkItemQueryRequest request, Long operatorId) {
        projectAccessGuard.requireReadable(request.getProjectId(), operatorId);
        LambdaQueryWrapper<PmWorkItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PmWorkItem::getProjectId, request.getProjectId());
        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            String keyKeyword = keyword.toUpperCase(Locale.ROOT);
            wrapper.and(w -> w.like(PmWorkItem::getTitle, keyword)
                    .or().like(PmWorkItem::getItemKey, keyKeyword));
        }
        wrapper.eq(request.getStatusId() != null, PmWorkItem::getStatusId, request.getStatusId());
        wrapper.eq(request.getAssigneeId() != null, PmWorkItem::getAssigneeId, request.getAssigneeId());
        wrapper.eq(request.getSprintId() != null, PmWorkItem::getSprintId, request.getSprintId());
        wrapper.eq(request.getParentId() != null, PmWorkItem::getParentId, request.getParentId());
        wrapper.eq(StringUtils.hasText(request.getType()), PmWorkItem::getType, request.getType());
        wrapper.eq(StringUtils.hasText(request.getPriority()), PmWorkItem::getPriority, request.getPriority());
        wrapper.orderByAsc(PmWorkItem::getRankValue).orderByAsc(PmWorkItem::getId);
        long current = Math.max(request.getCurrent(), 1L);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PmWorkItem> page = workItemMapper.selectPage(new Page<>(current, size), wrapper);
        Map<Long, PmStatus> statuses = statusMap(request.getProjectId());
        List<WorkItemVO> records = page.getRecords().stream()
                .map(item -> PmAssembler.toWorkItemVo(item, statuses.get(item.getStatusId())))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 查询工作项详情。
     *
     * @param id         工作项ID
     * @param operatorId 当前用户ID
     * @return 工作项详情
     */
    public WorkItemVO getDetail(Long id, Long operatorId) {
        return toVo(workItemGuard.requireReadable(id, operatorId));
    }

    /**
     * 创建工作项（支持 clientRequestId 幂等）。
     *
     * @param request    创建请求
     * @param operatorId 当前用户ID
     * @return 创建后的工作项
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkItemVO create(WorkItemSaveRequest request, Long operatorId) {
        projectAccessGuard.requireWritable(request.getProjectId(), operatorId);
        PmProject project = projectGuard.requireActiveProject(request.getProjectId());
        if (StringUtils.hasText(request.getClientRequestId())) {
            PmWorkItem existing = workItemMapper.selectOne(new LambdaQueryWrapper<PmWorkItem>()
                    .eq(PmWorkItem::getProjectId, project.getId())
                    .eq(PmWorkItem::getClientRequestId, request.getClientRequestId()));
            if (existing != null) {
                return toVo(existing);
            }
        }
        WorkItemType type = WorkItemType.from(request.getType());
        Priority priority = resolvePriority(request.getPriority());
        PmStatus status = resolveStatus(project.getId(), request.getStatusId());
        validateParent(project.getId(), type, request.getParentId(), null);
        long itemNo = nextItemNo(project.getId());
        PmWorkItem item = new PmWorkItem();
        item.setProjectId(project.getId());
        item.setItemNo((int) itemNo);
        item.setItemKey(project.getProjectKey() + "-" + itemNo);
        item.setType(type.name());
        item.setTitle(request.getTitle().trim());
        item.setDescription(request.getDescription());
        item.setStatusId(status.getId());
        item.setPriority(priority.name());
        item.setAssigneeId(request.getAssigneeId());
        item.setReporterId(request.getReporterId() == null ? operatorId : request.getReporterId());
        item.setParentId(request.getParentId());
        item.setSprintId(request.getSprintId());
        item.setStoryPoints(request.getStoryPoints());
        item.setDueDate(request.getDueDate());
        item.setLabels(request.getLabels());
        item.setExtensionJson(request.getExtensionJson());
        item.setRankValue(RankPolicy.nextAfter(maxRank(project.getId())));
        item.setVersion(INITIAL_VERSION);
        item.setClientRequestId(request.getClientRequestId());
        workItemMapper.insert(item);
        activityAppService.record(project.getId(), item.getId(), operatorId, ActivityAction.CREATE,
                null, null, item.getItemKey());
        notifyAssign(item, operatorId);
        log.info("工作项已创建: projectId={}, itemId={}, key={}", project.getId(), item.getId(), item.getItemKey());
        return PmAssembler.toWorkItemVo(item, status);
    }

    /**
     * 更新工作项（状态变更请走流转接口）。
     *
     * @param id         工作项ID
     * @param request    更新请求
     * @param operatorId 当前用户ID
     * @return 更新后的工作项
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkItemVO update(Long id, WorkItemSaveRequest request, Long operatorId) {
        PmWorkItem item = workItemGuard.requireWritable(id, operatorId);
        projectGuard.requireActiveProject(item.getProjectId());
        if (request.getVersion() != null && !request.getVersion().equals(item.getVersion())) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT, "工作项已被他人修改，请刷新后重试");
        }
        if (request.getStatusId() != null && !request.getStatusId().equals(item.getStatusId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态变更请使用流转接口");
        }
        WorkItemType type = WorkItemType.from(request.getType());
        Priority priority = resolvePriority(request.getPriority());
        validateParent(item.getProjectId(), type, request.getParentId(), id);
        Long previousAssignee = item.getAssigneeId();
        int affected = workItemMapper.update(null, new LambdaUpdateWrapper<PmWorkItem>()
                .eq(PmWorkItem::getId, id)
                .eq(PmWorkItem::getVersion, item.getVersion())
                .set(PmWorkItem::getType, type.name())
                .set(PmWorkItem::getTitle, request.getTitle().trim())
                .set(PmWorkItem::getDescription, request.getDescription())
                .set(PmWorkItem::getPriority, priority.name())
                .set(PmWorkItem::getAssigneeId, request.getAssigneeId())
                .set(PmWorkItem::getParentId, request.getParentId())
                .set(PmWorkItem::getSprintId, request.getSprintId())
                .set(PmWorkItem::getStoryPoints, request.getStoryPoints())
                .set(PmWorkItem::getDueDate, request.getDueDate())
                .set(PmWorkItem::getLabels, request.getLabels())
                .set(PmWorkItem::getExtensionJson, request.getExtensionJson())
                .set(PmWorkItem::getVersion, item.getVersion() + 1));
        if (affected == 0) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT, "工作项已被他人修改，请刷新后重试");
        }
        recordUpdateActivities(item, request, priority, operatorId);
        item.setType(type.name());
        item.setTitle(request.getTitle().trim());
        item.setDescription(request.getDescription());
        item.setPriority(priority.name());
        item.setAssigneeId(request.getAssigneeId());
        item.setParentId(request.getParentId());
        item.setSprintId(request.getSprintId());
        item.setStoryPoints(request.getStoryPoints());
        item.setDueDate(request.getDueDate());
        item.setLabels(request.getLabels());
        item.setExtensionJson(request.getExtensionJson());
        item.setVersion(item.getVersion() + 1);
        if (!Objects.equals(previousAssignee, request.getAssigneeId())) {
            notifyAssign(item, operatorId);
        }
        log.info("工作项已更新: itemId={}, version={}", id, item.getVersion());
        return toVo(item);
    }

    /**
     * 删除工作项并级联逻辑删除评论、附件与关联。
     *
     * @param id         工作项ID
     * @param operatorId 当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long operatorId) {
        PmWorkItem item = workItemGuard.requireWritable(id, operatorId);
        projectGuard.requireActiveProject(item.getProjectId());
        long children = workItemMapper.selectCount(new LambdaQueryWrapper<PmWorkItem>()
                .eq(PmWorkItem::getProjectId, item.getProjectId())
                .eq(PmWorkItem::getParentId, id));
        if (children > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "存在子工作项，请先删除或迁移子项");
        }
        commentMapper.delete(new LambdaQueryWrapper<PmComment>().eq(PmComment::getWorkItemId, id));
        attachmentMapper.delete(new LambdaQueryWrapper<com.bluewhale.pm.infrastructure.entity.PmAttachment>()
                .eq(com.bluewhale.pm.infrastructure.entity.PmAttachment::getWorkItemId, id));
        linkMapper.delete(new LambdaQueryWrapper<com.bluewhale.pm.infrastructure.entity.PmWorkItemLink>()
                .eq(com.bluewhale.pm.infrastructure.entity.PmWorkItemLink::getSourceId, id)
                .or().eq(com.bluewhale.pm.infrastructure.entity.PmWorkItemLink::getTargetId, id));
        activityAppService.record(item.getProjectId(), id, operatorId, ActivityAction.DELETE,
                null, item.getItemKey(), null);
        workItemMapper.deleteById(id);
        log.info("工作项已删除: itemId={}, key={}", id, item.getItemKey());
    }

    /**
     * 执行状态流转（必须命中项目配置的流转边）。
     *
     * @param id         工作项ID
     * @param request    流转请求
     * @param operatorId 当前用户ID
     * @return 流转后的工作项
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkItemVO transition(Long id, WorkItemTransitionRequest request, Long operatorId) {
        PmWorkItem item = workItemGuard.requireWritable(id, operatorId);
        projectGuard.requireActiveProject(item.getProjectId());
        PmStatus target = requireStatusInProject(item.getProjectId(), request.getTargetStatusId());
        PmTransition transition = transitionMapper.selectOne(new LambdaQueryWrapper<PmTransition>()
                .eq(PmTransition::getProjectId, item.getProjectId())
                .eq(PmTransition::getFromStatusId, item.getStatusId())
                .eq(PmTransition::getToStatusId, target.getId()));
        if (transition == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未配置该状态流转");
        }
        if (Integer.valueOf(FLAG_TRUE).equals(transition.getRequireComment())
                && !StringUtils.hasText(request.getComment())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该流转要求填写评论");
        }
        PmStatus from = statusMapper.selectById(item.getStatusId());
        int affected = workItemMapper.update(null, new LambdaUpdateWrapper<PmWorkItem>()
                .eq(PmWorkItem::getId, id)
                .eq(PmWorkItem::getVersion, item.getVersion())
                .set(PmWorkItem::getStatusId, target.getId())
                .set(PmWorkItem::getVersion, item.getVersion() + 1));
        if (affected == 0) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT, "工作项已被他人修改，请刷新后重试");
        }
        if (StringUtils.hasText(request.getComment())) {
            PmComment comment = new PmComment();
            comment.setProjectId(item.getProjectId());
            comment.setWorkItemId(id);
            comment.setAuthorId(operatorId);
            comment.setContent(request.getComment().trim());
            commentMapper.insert(comment);
        }
        String oldCode = from == null ? null : from.getCode();
        activityAppService.record(item.getProjectId(), id, operatorId, ActivityAction.TRANSITION,
                "status", oldCode, target.getCode());
        item.setStatusId(target.getId());
        item.setVersion(item.getVersion() + 1);
        notifyTransition(item, operatorId, from, target);
        log.info("工作项状态已流转: itemId={}, from={}, to={}", id, oldCode, target.getCode());
        return PmAssembler.toWorkItemVo(item, target);
    }

    /**
     * 调整工作项排序（移动到两个锚点之间）。
     *
     * @param id         工作项ID
     * @param request    排序请求
     * @param operatorId 当前用户ID
     * @return 排序后的工作项
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkItemVO rank(Long id, WorkItemRankRequest request, Long operatorId) {
        PmWorkItem item = workItemGuard.requireWritable(id, operatorId);
        projectGuard.requireActiveProject(item.getProjectId());
        if (request.getBeforeItemId() == null && request.getAfterItemId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "排序锚点不能同时为空");
        }
        PmWorkItem before = anchor(request.getBeforeItemId(), item.getProjectId(), id);
        PmWorkItem after = anchor(request.getAfterItemId(), item.getProjectId(), id);
        long lower = before == null ? 0L : RankPolicy.parse(before.getRankValue());
        Long upper = after == null ? null : RankPolicy.parse(after.getRankValue());
        if (upper != null && lower >= upper) {
            throw new BizException(ErrorCode.PARAM_ERROR, "排序锚点顺序不正确");
        }
        String newRank = RankPolicy.between(lower, upper);
        if (newRank == null) {
            rebalance(item.getProjectId(), id);
            lower = before == null ? 0L : RankPolicy.parse(reloadRank(before.getId()));
            upper = after == null ? null : RankPolicy.parse(reloadRank(after.getId()));
            newRank = RankPolicy.between(lower, upper);
        }
        if (newRank == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "排序空间不足，请重试");
        }
        int affected = workItemMapper.update(null, new LambdaUpdateWrapper<PmWorkItem>()
                .eq(PmWorkItem::getId, id)
                .eq(PmWorkItem::getVersion, item.getVersion())
                .set(PmWorkItem::getRankValue, newRank)
                .set(PmWorkItem::getVersion, item.getVersion() + 1));
        if (affected == 0) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT, "工作项已被他人修改，请刷新后重试");
        }
        activityAppService.record(item.getProjectId(), id, operatorId, ActivityAction.UPDATE,
                "rank", item.getRankValue(), newRank);
        item.setRankValue(newRank);
        item.setVersion(item.getVersion() + 1);
        log.info("工作项排序已调整: itemId={}, rank={}", id, newRank);
        return toVo(item);
    }

    /**
     * 查询工作项动态。
     *
     * @param id         工作项ID
     * @param operatorId 当前用户ID
     * @return 动态列表
     */
    public List<ActivityVO> listActivities(Long id, Long operatorId) {
        workItemGuard.requireReadable(id, operatorId);
        return activityAppService.listByItem(id);
    }

    /**
     * 查询项目级动态。
     *
     * @param projectId  项目ID
     * @param operatorId 当前用户ID
     * @return 动态列表
     */
    public List<ActivityVO> listProjectActivities(Long projectId, Long operatorId) {
        projectAccessGuard.requireReadable(projectId, operatorId);
        return activityAppService.listByProject(projectId);
    }

    /**
     * 记录更新产生的字段变更动态。
     *
     * @param item       更新前的工作项
     * @param request    更新请求
     * @param priority   新优先级
     * @param operatorId 当前用户ID
     */
    private void recordUpdateActivities(PmWorkItem item, WorkItemSaveRequest request, Priority priority,
            Long operatorId) {
        String newTitle = request.getTitle().trim();
        if (!Objects.equals(item.getTitle(), newTitle)) {
            activityAppService.record(item.getProjectId(), item.getId(), operatorId, ActivityAction.UPDATE,
                    "title", item.getTitle(), newTitle);
        }
        if (!Objects.equals(item.getPriority(), priority.name())) {
            activityAppService.record(item.getProjectId(), item.getId(), operatorId, ActivityAction.UPDATE,
                    "priority", item.getPriority(), priority.name());
        }
        if (!Objects.equals(item.getAssigneeId(), request.getAssigneeId())) {
            activityAppService.record(item.getProjectId(), item.getId(), operatorId, ActivityAction.UPDATE,
                    "assignee", asText(item.getAssigneeId()), asText(request.getAssigneeId()));
        }
        if (!Objects.equals(item.getDueDate(), request.getDueDate())) {
            activityAppService.record(item.getProjectId(), item.getId(), operatorId, ActivityAction.UPDATE,
                    "dueDate", asText(item.getDueDate()), asText(request.getDueDate()));
        }
    }

    /**
     * 解析优先级，空值取默认中优先级。
     *
     * @param code 优先级编码
     * @return 优先级
     */
    private Priority resolvePriority(String code) {
        return StringUtils.hasText(code) ? Priority.from(code) : Priority.from(DEFAULT_PRIORITY);
    }

    /**
     * 解析工作项状态：指定时校验归属，未指定时取项目默认状态。
     *
     * @param projectId 项目ID
     * @param statusId  状态ID，可为 {@code null}
     * @return 状态实体
     */
    private PmStatus resolveStatus(Long projectId, Long statusId) {
        if (statusId != null) {
            return requireStatusInProject(projectId, statusId);
        }
        PmStatus defaultStatus = statusMapper.selectOne(new LambdaQueryWrapper<PmStatus>()
                .eq(PmStatus::getProjectId, projectId)
                .eq(PmStatus::getIsDefault, FLAG_TRUE)
                .last("LIMIT 1"));
        if (defaultStatus == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "项目未配置默认状态，请先完善状态配置");
        }
        return defaultStatus;
    }

    /**
     * 校验状态存在且属于指定项目。
     *
     * @param projectId 项目ID
     * @param statusId  状态ID
     * @return 状态实体
     */
    private PmStatus requireStatusInProject(Long projectId, Long statusId) {
        PmStatus status = statusId == null ? null : statusMapper.selectById(statusId);
        if (status == null || !projectId.equals(status.getProjectId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态不存在或不属于当前项目");
        }
        return status;
    }

    /**
     * 校验父子关系：子任务必须有父项，父项同项目，禁止自引用与环。
     *
     * @param projectId 项目ID
     * @param type      工作项类型
     * @param parentId  父工作项ID
     * @param selfId    当前工作项ID，创建时传 {@code null}
     */
    private void validateParent(Long projectId, WorkItemType type, Long parentId, Long selfId) {
        if (type == WorkItemType.SUBTASK && parentId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "子任务必须指定父工作项");
        }
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "父工作项不能是自身");
        }
        PmWorkItem parent = workItemMapper.selectById(parentId);
        if (parent == null || !projectId.equals(parent.getProjectId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "父工作项不存在或不属于当前项目");
        }
        PmWorkItem cursor = parent;
        int depth = 0;
        while (cursor.getParentId() != null && depth < MAX_PARENT_DEPTH) {
            if (cursor.getParentId().equals(selfId)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "父子关系存在环");
            }
            cursor = workItemMapper.selectById(cursor.getParentId());
            if (cursor == null) {
                return;
            }
            depth++;
        }
    }

    /**
     * 生成项目内下一个序号，与库内最大值取较大者兜底。
     *
     * @param projectId 项目ID
     * @return 序号
     */
    private long nextItemNo(Long projectId) {
        long sequence = sequenceGenerator.next(projectId);
        return Math.max(sequence, maxItemNo(projectId) + 1);
    }

    /**
     * 查询项目内最大工作项序号。
     *
     * @param projectId 项目ID
     * @return 最大序号，无数据返回 0
     */
    private long maxItemNo(Long projectId) {
        PmWorkItem last = workItemMapper.selectOne(new LambdaQueryWrapper<PmWorkItem>()
                .eq(PmWorkItem::getProjectId, projectId)
                .orderByDesc(PmWorkItem::getItemNo)
                .last("LIMIT 1"));
        return last == null || last.getItemNo() == null ? 0L : last.getItemNo();
    }

    /**
     * 查询项目内最大排序值。
     *
     * @param projectId 项目ID
     * @return 最大排序值，无数据返回 {@code null}
     */
    private String maxRank(Long projectId) {
        PmWorkItem last = workItemMapper.selectOne(new LambdaQueryWrapper<PmWorkItem>()
                .eq(PmWorkItem::getProjectId, projectId)
                .orderByDesc(PmWorkItem::getRankValue)
                .last("LIMIT 1"));
        return last == null ? null : last.getRankValue();
    }

    /**
     * 校验排序锚点：非自身且属于当前项目。
     *
     * @param anchorId  锚点工作项ID
     * @param projectId 项目ID
     * @param selfId    被移动的工作项ID
     * @return 锚点工作项，入参为空时返回 {@code null}
     */
    private PmWorkItem anchor(Long anchorId, Long projectId, Long selfId) {
        if (anchorId == null) {
            return null;
        }
        if (anchorId.equals(selfId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "排序锚点不能是自身");
        }
        return workItemGuard.requireInProject(anchorId, projectId);
    }

    /**
     * 项目内局部重排：先把所有项移到临时值，再以 1000 步长重排，避免唯一键冲突。
     *
     * @param projectId 项目ID
     * @param excludeId 被移动的工作项ID（保持临时值，由后续步骤赋值）
     */
    private void rebalance(Long projectId, Long excludeId) {
        workItemMapper.update(null, new LambdaUpdateWrapper<PmWorkItem>()
                .eq(PmWorkItem::getId, excludeId)
                .set(PmWorkItem::getRankValue, TEMP_RANK_PREFIX + excludeId));
        List<PmWorkItem> items = workItemMapper.selectList(new LambdaQueryWrapper<PmWorkItem>()
                .eq(PmWorkItem::getProjectId, projectId)
                .orderByAsc(PmWorkItem::getRankValue)
                .orderByAsc(PmWorkItem::getId)).stream()
                .filter(candidate -> !candidate.getId().equals(excludeId))
                .toList();
        for (PmWorkItem candidate : items) {
            workItemMapper.update(null, new LambdaUpdateWrapper<PmWorkItem>()
                    .eq(PmWorkItem::getId, candidate.getId())
                    .set(PmWorkItem::getRankValue, TEMP_RANK_PREFIX + candidate.getId()));
        }
        long sequence = 1000L;
        for (PmWorkItem candidate : items) {
            workItemMapper.update(null, new LambdaUpdateWrapper<PmWorkItem>()
                    .eq(PmWorkItem::getId, candidate.getId())
                    .set(PmWorkItem::getRankValue, RankPolicy.format(sequence)));
            sequence += 1000L;
        }
        log.info("工作项排序已局部重排: projectId={}, count={}", projectId, items.size());
    }

    /**
     * 重新读取工作项排序值。
     *
     * @param itemId 工作项ID
     * @return 排序值
     */
    private String reloadRank(Long itemId) {
        PmWorkItem reloaded = workItemMapper.selectById(itemId);
        return reloaded == null ? null : reloaded.getRankValue();
    }

    /**
     * 查询项目状态并转为 ID 映射。
     *
     * @param projectId 项目ID
     * @return 状态映射
     */
    private Map<Long, PmStatus> statusMap(Long projectId) {
        return statusMapper.selectList(new LambdaQueryWrapper<PmStatus>()
                .eq(PmStatus::getProjectId, projectId)).stream()
                .collect(Collectors.toMap(PmStatus::getId, status -> status));
    }

    /**
     * 工作项转视图对象（自动补齐状态信息）。
     *
     * @param item 工作项实体
     * @return 工作项视图对象
     */
    private WorkItemVO toVo(PmWorkItem item) {
        return PmAssembler.toWorkItemVo(item, statusMapper.selectById(item.getStatusId()));
    }

    /**
     * 长整型转字符串，空值返回 {@code null}
     *
     * @param value 值
     * @return 字符串
     */
    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 发布「被指派」通知（无负责人时跳过，发布器再过滤本人）。
     *
     * @param item       工作项
     * @param operatorId 操作人用户ID
     */
    private void notifyAssign(PmWorkItem item, Long operatorId) {
        if (item.getAssigneeId() == null) {
            return;
        }
        notifyPublisher.publish(PmNotifyEvent.builder()
                .projectId(item.getProjectId())
                .workItemId(item.getId())
                .itemKey(item.getItemKey())
                .itemTitle(item.getTitle())
                .kind(PmNotifyKind.ASSIGN)
                .operatorId(operatorId)
                .recipientIds(PmNotifyEvent.recipients(item.getAssigneeId()))
                .detail("你被指派为该工作项的负责人")
                .build());
    }

    /**
     * 发布「状态变更」通知。
     *
     * @param item       工作项
     * @param operatorId 操作人用户ID
     * @param from       原状态
     * @param to         目标状态
     */
    private void notifyTransition(PmWorkItem item, Long operatorId, PmStatus from, PmStatus to) {
        if (item.getAssigneeId() == null) {
            return;
        }
        String fromName = from == null ? "未知状态" : from.getName();
        notifyPublisher.publish(PmNotifyEvent.builder()
                .projectId(item.getProjectId())
                .workItemId(item.getId())
                .itemKey(item.getItemKey())
                .itemTitle(item.getTitle())
                .kind(PmNotifyKind.TRANSITION)
                .operatorId(operatorId)
                .recipientIds(PmNotifyEvent.recipients(item.getAssigneeId()))
                .detail("状态由「" + fromName + "」变更为「" + to.getName() + "」")
                .build());
    }
}
