package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pm.application.assembler.PmAssembler;
import com.bluewhale.pm.domain.model.ActivityAction;
import com.bluewhale.pm.domain.model.LinkType;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.entity.PmWorkItemLink;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemLinkMapper;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemMapper;
import com.bluewhale.pm.interfaces.dto.WorkItemLinkSaveRequest;
import com.bluewhale.pm.interfaces.vo.WorkItemLinkVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作项关联应用服务：只维护单向边（源 → 目标），反向关系由调用方显式选择反向类型。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkItemLinkAppService {

    private final PmWorkItemLinkMapper linkMapper;

    private final PmWorkItemMapper workItemMapper;

    private final ProjectGuard projectGuard;

    private final WorkItemGuard workItemGuard;

    private final WorkItemActivityAppService activityAppService;

    /**
     * 查询工作项作为源的全部关联（按创建时间升序）。
     *
     * @param workItemId 工作项ID
     * @param operatorId 当前用户ID
     * @return 关联列表
     */
    public List<WorkItemLinkVO> list(Long workItemId, Long operatorId) {
        workItemGuard.requireReadable(workItemId, operatorId);
        List<PmWorkItemLink> links = linkMapper.selectList(new LambdaQueryWrapper<PmWorkItemLink>()
                .eq(PmWorkItemLink::getSourceId, workItemId)
                .orderByAsc(PmWorkItemLink::getCreateTime)
                .orderByAsc(PmWorkItemLink::getId));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> targetIds = links.stream().map(PmWorkItemLink::getTargetId).distinct().toList();
        Map<Long, PmWorkItem> targets = workItemMapper.selectBatchIds(targetIds).stream()
                .collect(Collectors.toMap(PmWorkItem::getId, Function.identity()));
        return links.stream()
                .map(link -> PmAssembler.toLinkVo(link, targets.get(link.getTargetId())))
                .toList();
    }

    /**
     * 新增工作项关联（同项目、非自身、同类型不可重复）。
     *
     * @param workItemId 源工作项ID
     * @param request    新增请求
     * @param operatorId 当前用户ID
     * @return 新增后的关联
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkItemLinkVO add(Long workItemId, WorkItemLinkSaveRequest request, Long operatorId) {
        PmWorkItem source = workItemGuard.requireWritable(workItemId, operatorId);
        projectGuard.requireActiveProject(source.getProjectId());
        LinkType linkType = LinkType.from(request.getLinkType());
        if (workItemId.equals(request.getTargetItemId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不能关联工作项自身");
        }
        PmWorkItem target = workItemGuard.requireInProject(request.getTargetItemId(), source.getProjectId());
        Long exists = linkMapper.selectCount(new LambdaQueryWrapper<PmWorkItemLink>()
                .eq(PmWorkItemLink::getSourceId, workItemId)
                .eq(PmWorkItemLink::getTargetId, target.getId())
                .eq(PmWorkItemLink::getLinkType, linkType.name()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该关联已存在");
        }
        PmWorkItemLink link = new PmWorkItemLink();
        link.setProjectId(source.getProjectId());
        link.setSourceId(workItemId);
        link.setTargetId(target.getId());
        link.setLinkType(linkType.name());
        linkMapper.insert(link);
        activityAppService.record(source.getProjectId(), workItemId, operatorId, ActivityAction.LINK,
                "linkType", null, linkType.name() + " -> " + target.getItemKey());
        log.info("工作项关联已新增: sourceId={}, targetId={}, type={}", workItemId, target.getId(), linkType.name());
        return PmAssembler.toLinkVo(link, target);
    }

    /**
     * 删除工作项关联（以源工作项的项目写权限为准）。
     *
     * @param linkId     关联记录ID
     * @param operatorId 当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long linkId, Long operatorId) {
        PmWorkItemLink link = linkId == null ? null : linkMapper.selectById(linkId);
        if (link == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作项关联不存在");
        }
        workItemGuard.requireWritable(link.getSourceId(), operatorId);
        projectGuard.requireActiveProject(link.getProjectId());
        linkMapper.deleteById(linkId);
        activityAppService.record(link.getProjectId(), link.getSourceId(), operatorId, ActivityAction.LINK,
                "linkType", link.getLinkType(), null);
        log.info("工作项关联已删除: linkId={}, sourceId={}", linkId, link.getSourceId());
    }
}
