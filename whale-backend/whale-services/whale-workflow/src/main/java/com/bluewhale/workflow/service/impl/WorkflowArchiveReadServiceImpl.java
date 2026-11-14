package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.converter.WorkflowConverter;
import com.bluewhale.workflow.po.ProcessDefinitionPo;
import com.bluewhale.workflow.po.ProcessDefinitionVersionPo;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.mapper.ProcessDefinitionMapper;
import com.bluewhale.workflow.mapper.ProcessDefinitionVersionMapper;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.service.WorkflowArchiveReadService;
import com.bluewhale.workflow.support.WorkflowConstants;
import com.bluewhale.workflow.vo.ApprovedInstanceVO;
import com.bluewhale.workflow.vo.ArchiveInstanceDetailVO;
import com.bluewhale.workflow.vo.ProcessTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程实例归档只读服务实现。
 *
 * <p>办结时间优先取 {@code process_instance.finished_at}（审批通过时写入）；
 * 历史数据该列为空时回落 {@code update_time}，保证档案到期计算不中断。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class WorkflowArchiveReadServiceImpl implements WorkflowArchiveReadService {

    private final ProcessDefinitionMapper processDefinitionMapper;

    private final ProcessDefinitionVersionMapper processDefinitionVersionMapper;

    private final ProcessInstanceMapper processInstanceMapper;

    private final ProcessTaskMapper processTaskMapper;

    @Override
    public PageResult<ApprovedInstanceVO> approved(LocalDateTime updatedAfter, LocalDateTime updatedBefore,
                                                   long current, long size) {
        long pageCurrent = Math.min(Math.max(current, 1L), WorkflowConstants.MAX_PAGE_CURRENT);
        long pageSize = Math.min(Math.max(size, 1L), WorkflowConstants.MAX_PAGE_SIZE);
        LambdaQueryWrapper<ProcessInstancePo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessInstancePo::getStatus, WorkflowConstants.INSTANCE_APPROVED);
        wrapper.gt(updatedAfter != null, ProcessInstancePo::getFinishedAt, updatedAfter);
        wrapper.le(updatedBefore != null, ProcessInstancePo::getFinishedAt, updatedBefore);
        wrapper.orderByAsc(ProcessInstancePo::getFinishedAt);
        wrapper.orderByAsc(ProcessInstancePo::getId);
        Page<ProcessInstancePo> page = processInstanceMapper.selectPage(new Page<>(pageCurrent, pageSize), wrapper);
        List<ApprovedInstanceVO> records = page.getRecords().stream()
                .map(WorkflowArchiveReadServiceImpl::toApprovedVo)
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ArchiveInstanceDetailVO detail(Long id) {
        ProcessInstancePo instance = processInstanceMapper.selectById(id);
        if (instance == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        ArchiveInstanceDetailVO vo = WorkflowConverter.INSTANCE.toArchiveDetailVo(instance);
        vo.setFinishedAt(resolveFinishedAt(instance));
        vo.setDefinitionJson(resolveDefinitionJson(instance));
        vo.setTasks(listTasks(id));
        return vo;
    }

    /**
     * 解析实例所用定义快照 JSON：优先实例版本快照，其次最新已发布版本，最后回落定义当前内容。
     *
     * @param instance 流程实例实体
     * @return 定义快照 JSON；定义不存在时返回 {@code null}
     */
    private String resolveDefinitionJson(ProcessInstancePo instance) {
        Long definitionId = instance.getDefinitionId();
        if (definitionId == null) {
            return null;
        }
        ProcessDefinitionVersionPo snapshot = findVersionSnapshot(definitionId, instance.getDefinitionVersion());
        if (snapshot == null) {
            snapshot = findPublishedSnapshot(definitionId);
        }
        if (snapshot != null) {
            return snapshot.getDefinitionJson();
        }
        ProcessDefinitionPo definition = processDefinitionMapper.selectById(definitionId);
        return definition == null ? null : definition.getDefinitionJson();
    }

    /**
     * 查询指定版本的已发布快照。
     *
     * @param definitionId 流程定义ID
     * @param version 版本号，可为 {@code null}
     * @return 版本快照；不存在返回 {@code null}
     */
    private ProcessDefinitionVersionPo findVersionSnapshot(Long definitionId, Integer version) {
        if (version == null) {
            return null;
        }
        LambdaQueryWrapper<ProcessDefinitionVersionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessDefinitionVersionPo::getDefinitionId, definitionId);
        wrapper.eq(ProcessDefinitionVersionPo::getVersion, version);
        List<ProcessDefinitionVersionPo> snapshots = processDefinitionVersionMapper.selectList(wrapper);
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }

    /**
     * 查询定义当前已发布的版本快照（版本号最大者）。
     *
     * @param definitionId 流程定义ID
     * @return 已发布快照；不存在返回 {@code null}
     */
    private ProcessDefinitionVersionPo findPublishedSnapshot(Long definitionId) {
        LambdaQueryWrapper<ProcessDefinitionVersionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessDefinitionVersionPo::getDefinitionId, definitionId);
        wrapper.eq(ProcessDefinitionVersionPo::getPublishStatus, WorkflowConstants.PUBLISH_PUBLISHED);
        wrapper.orderByDesc(ProcessDefinitionVersionPo::getVersion);
        List<ProcessDefinitionVersionPo> snapshots = processDefinitionVersionMapper.selectList(wrapper);
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }

    /**
     * 查询实例下的任务轨迹（节点下标、创建时间升序）。
     *
     * @param instanceId 流程实例ID
     * @return 任务视图对象列表
     */
    private List<ProcessTaskVO> listTasks(Long instanceId) {
        LambdaQueryWrapper<ProcessTaskPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTaskPo::getInstanceId, instanceId);
        wrapper.orderByAsc(ProcessTaskPo::getNodeIndex);
        wrapper.orderByAsc(ProcessTaskPo::getCreateTime);
        return processTaskMapper.selectList(wrapper).stream()
                .map(WorkflowArchiveReadServiceImpl::toTaskVo)
                .toList();
    }

    /**
     * 实例实体转已通过摘要视图对象。
     *
     * @param entity 流程实例实体
     * @return 已通过实例摘要视图对象
     */
    private static ApprovedInstanceVO toApprovedVo(ProcessInstancePo entity) {
        ApprovedInstanceVO vo = WorkflowConverter.INSTANCE.toApprovedVo(entity);
        vo.setFinishedAt(resolveFinishedAt(entity));
        return vo;
    }

    /**
     * 解析办结时间：优先独立字段，历史数据为空时回落更新时间。
     *
     * @param instance 流程实例
     * @return 办结时间
     */
    private static LocalDateTime resolveFinishedAt(ProcessInstancePo instance) {
        return instance.getFinishedAt() != null ? instance.getFinishedAt() : instance.getUpdateTime();
    }

    /**
     * 任务实体转视图对象。
     *
     * @param entity 待办任务实体
     * @return 待办任务视图对象
     */
    private static ProcessTaskVO toTaskVo(ProcessTaskPo entity) {
        return WorkflowConverter.INSTANCE.toTaskVo(entity);
    }
}
