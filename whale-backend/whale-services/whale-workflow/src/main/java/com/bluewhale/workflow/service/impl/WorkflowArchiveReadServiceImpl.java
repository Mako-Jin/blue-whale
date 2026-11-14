package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.converter.WorkflowConverter;
import com.bluewhale.workflow.entity.ProcessDefinition;
import com.bluewhale.workflow.entity.ProcessDefinitionVersion;
import com.bluewhale.workflow.entity.ProcessInstance;
import com.bluewhale.workflow.entity.ProcessTask;
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
        LambdaQueryWrapper<ProcessInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessInstance::getStatus, WorkflowConstants.INSTANCE_APPROVED);
        wrapper.gt(updatedAfter != null, ProcessInstance::getFinishedAt, updatedAfter);
        wrapper.le(updatedBefore != null, ProcessInstance::getFinishedAt, updatedBefore);
        wrapper.orderByAsc(ProcessInstance::getFinishedAt);
        wrapper.orderByAsc(ProcessInstance::getId);
        Page<ProcessInstance> page = processInstanceMapper.selectPage(new Page<>(pageCurrent, pageSize), wrapper);
        List<ApprovedInstanceVO> records = page.getRecords().stream()
                .map(WorkflowArchiveReadServiceImpl::toApprovedVo)
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ArchiveInstanceDetailVO detail(Long id) {
        ProcessInstance instance = processInstanceMapper.selectById(id);
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
    private String resolveDefinitionJson(ProcessInstance instance) {
        Long definitionId = instance.getDefinitionId();
        if (definitionId == null) {
            return null;
        }
        ProcessDefinitionVersion snapshot = findVersionSnapshot(definitionId, instance.getDefinitionVersion());
        if (snapshot == null) {
            snapshot = findPublishedSnapshot(definitionId);
        }
        if (snapshot != null) {
            return snapshot.getDefinitionJson();
        }
        ProcessDefinition definition = processDefinitionMapper.selectById(definitionId);
        return definition == null ? null : definition.getDefinitionJson();
    }

    /**
     * 查询指定版本的已发布快照。
     *
     * @param definitionId 流程定义ID
     * @param version 版本号，可为 {@code null}
     * @return 版本快照；不存在返回 {@code null}
     */
    private ProcessDefinitionVersion findVersionSnapshot(Long definitionId, Integer version) {
        if (version == null) {
            return null;
        }
        LambdaQueryWrapper<ProcessDefinitionVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessDefinitionVersion::getDefinitionId, definitionId);
        wrapper.eq(ProcessDefinitionVersion::getVersion, version);
        List<ProcessDefinitionVersion> snapshots = processDefinitionVersionMapper.selectList(wrapper);
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }

    /**
     * 查询定义当前已发布的版本快照（版本号最大者）。
     *
     * @param definitionId 流程定义ID
     * @return 已发布快照；不存在返回 {@code null}
     */
    private ProcessDefinitionVersion findPublishedSnapshot(Long definitionId) {
        LambdaQueryWrapper<ProcessDefinitionVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessDefinitionVersion::getDefinitionId, definitionId);
        wrapper.eq(ProcessDefinitionVersion::getPublishStatus, WorkflowConstants.PUBLISH_PUBLISHED);
        wrapper.orderByDesc(ProcessDefinitionVersion::getVersion);
        List<ProcessDefinitionVersion> snapshots = processDefinitionVersionMapper.selectList(wrapper);
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }

    /**
     * 查询实例下的任务轨迹（节点下标、创建时间升序）。
     *
     * @param instanceId 流程实例ID
     * @return 任务视图对象列表
     */
    private List<ProcessTaskVO> listTasks(Long instanceId) {
        LambdaQueryWrapper<ProcessTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTask::getInstanceId, instanceId);
        wrapper.orderByAsc(ProcessTask::getNodeIndex);
        wrapper.orderByAsc(ProcessTask::getCreateTime);
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
    private static ApprovedInstanceVO toApprovedVo(ProcessInstance entity) {
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
    private static LocalDateTime resolveFinishedAt(ProcessInstance instance) {
        return instance.getFinishedAt() != null ? instance.getFinishedAt() : instance.getUpdateTime();
    }

    /**
     * 任务实体转视图对象。
     *
     * @param entity 待办任务实体
     * @return 待办任务视图对象
     */
    private static ProcessTaskVO toTaskVo(ProcessTask entity) {
        return WorkflowConverter.INSTANCE.toTaskVo(entity);
    }
}
