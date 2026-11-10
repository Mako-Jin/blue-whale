package com.bluewhale.oa.approval.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.oa.approval.domain.model.ApprovalInstance;
import com.bluewhale.oa.approval.domain.model.ApprovalInstanceQuery;
import com.bluewhale.oa.approval.domain.model.ApprovalStatus;
import com.bluewhale.oa.approval.domain.repository.ApprovalInstanceRepository;
import com.bluewhale.oa.approval.infrastructure.persistence.entity.ApprovalInstanceEntity;
import com.bluewhale.oa.approval.infrastructure.persistence.mapper.ApprovalInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 审批实例仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ApprovalInstanceRepositoryImpl implements ApprovalInstanceRepository {

    private final ApprovalInstanceMapper approvalInstanceMapper;

    @Override
    public List<ApprovalInstance> page(ApprovalInstanceQuery query) {
        Page<ApprovalInstanceEntity> page = approvalInstanceMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L), false), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ApprovalInstanceQuery query) {
        Long total = approvalInstanceMapper.selectCount(filter(query));
        return total == null ? 0L : total;
    }

    @Override
    public Optional<ApprovalInstance> findById(Long id) {
        return Optional.ofNullable(approvalInstanceMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<ApprovalInstance> findByBusiness(String businessType, Long businessId) {
        return Optional.ofNullable(approvalInstanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstanceEntity>()
                .eq(ApprovalInstanceEntity::getBusinessType, businessType)
                .eq(ApprovalInstanceEntity::getBusinessId, businessId)
                .last("limit 1"))).map(this::toDomain);
    }

    @Override
    public Optional<ApprovalInstance> findByWorkflowInstanceId(Long workflowInstanceId) {
        if (workflowInstanceId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(approvalInstanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstanceEntity>()
                .eq(ApprovalInstanceEntity::getWorkflowInstanceId, workflowInstanceId)
                .last("limit 1"))).map(this::toDomain);
    }

    @Override
    public List<ApprovalInstance> findByWorkflowInstanceIds(List<Long> workflowInstanceIds) {
        if (workflowInstanceIds == null || workflowInstanceIds.isEmpty()) {
            return List.of();
        }
        return approvalInstanceMapper.selectList(new LambdaQueryWrapper<ApprovalInstanceEntity>()
                        .in(ApprovalInstanceEntity::getWorkflowInstanceId, workflowInstanceIds))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<ApprovalInstance> findRunningSubmittedBefore(LocalDateTime submittedBefore, int limit) {
        return approvalInstanceMapper.selectList(new LambdaQueryWrapper<ApprovalInstanceEntity>()
                        .eq(ApprovalInstanceEntity::getStatus, ApprovalStatus.RUNNING.name())
                        .isNotNull(ApprovalInstanceEntity::getWorkflowInstanceId)
                        .le(ApprovalInstanceEntity::getSubmittedAt, submittedBefore)
                        .orderByAsc(ApprovalInstanceEntity::getSubmittedAt)
                        .last("limit " + Math.max(limit, 1)))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<ApprovalInstance> findRunningWithWorkflow(int limit) {
        return approvalInstanceMapper.selectList(new LambdaQueryWrapper<ApprovalInstanceEntity>()
                        .eq(ApprovalInstanceEntity::getStatus, ApprovalStatus.RUNNING.name())
                        .isNotNull(ApprovalInstanceEntity::getWorkflowInstanceId)
                        .orderByAsc(ApprovalInstanceEntity::getSubmittedAt)
                        .last("limit " + Math.max(limit, 1)))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public boolean insertIfAbsent(ApprovalInstance instance) {
        if (findByBusiness(instance.getBusinessType(), instance.getBusinessId()).isPresent()) {
            return false;
        }
        try {
            ApprovalInstanceEntity entity = new ApprovalInstanceEntity();
            fill(entity, instance);
            approvalInstanceMapper.insert(entity);
            instance.setId(entity.getId());
            instance.setCreateTime(entity.getCreateTime());
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public void update(ApprovalInstance instance) {
        approvalInstanceMapper.update(null, new LambdaUpdateWrapper<ApprovalInstanceEntity>()
                .eq(ApprovalInstanceEntity::getId, instance.getId())
                .set(ApprovalInstanceEntity::getWorkflowInstanceId, instance.getWorkflowInstanceId())
                .set(ApprovalInstanceEntity::getStatus, instance.getStatus() == null ? null : instance.getStatus().name())
                .set(ApprovalInstanceEntity::getCurrentNodeKey, instance.getCurrentNodeKey())
                .set(ApprovalInstanceEntity::getCurrentNodeName, instance.getCurrentNodeName())
                .set(ApprovalInstanceEntity::getCurrentAssignees, instance.getCurrentAssignees())
                .set(ApprovalInstanceEntity::getFinishedAt, instance.getFinishedAt()));
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<ApprovalInstanceEntity> filter(ApprovalInstanceQuery query) {
        LambdaQueryWrapper<ApprovalInstanceEntity> wrapper = new LambdaQueryWrapper<ApprovalInstanceEntity>()
                .eq(StringUtils.hasText(query.getBusinessType()),
                        ApprovalInstanceEntity::getBusinessType, query.getBusinessType())
                .eq(StringUtils.hasText(query.getStatus()),
                        ApprovalInstanceEntity::getStatus, query.getStatus())
                .eq(query.getInitiatorId() != null, ApprovalInstanceEntity::getInitiatorId, query.getInitiatorId())
                .eq(query.getBusinessId() != null, ApprovalInstanceEntity::getBusinessId, query.getBusinessId());
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(item -> item.like(ApprovalInstanceEntity::getTitle, query.getKeyword())
                    .or().like(ApprovalInstanceEntity::getBusinessCode, query.getKeyword()));
        }
        return wrapper.orderByDesc(ApprovalInstanceEntity::getCreateTime);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private ApprovalInstance toDomain(ApprovalInstanceEntity entity) {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(entity.getId());
        instance.setBusinessType(entity.getBusinessType());
        instance.setBusinessId(entity.getBusinessId());
        instance.setBusinessCode(entity.getBusinessCode());
        instance.setDefinitionCode(entity.getDefinitionCode());
        instance.setWorkflowInstanceId(entity.getWorkflowInstanceId());
        instance.setTitle(entity.getTitle());
        instance.setInitiatorId(entity.getInitiatorId());
        instance.setInitiatorName(entity.getInitiatorName());
        instance.setStatus(entity.getStatus() == null ? ApprovalStatus.RUNNING : ApprovalStatus.valueOf(entity.getStatus()));
        instance.setCurrentNodeKey(entity.getCurrentNodeKey());
        instance.setCurrentNodeName(entity.getCurrentNodeName());
        instance.setCurrentAssignees(entity.getCurrentAssignees());
        instance.setSubmittedAt(entity.getSubmittedAt());
        instance.setFinishedAt(entity.getFinishedAt());
        instance.setBusinessUrl(entity.getBusinessUrl());
        instance.setRemark(entity.getRemark());
        instance.setCreateTime(entity.getCreateTime());
        instance.setUpdateTime(entity.getUpdateTime());
        return instance;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param instance 领域模型
     */
    private void fill(ApprovalInstanceEntity entity, ApprovalInstance instance) {
        entity.setBusinessType(instance.getBusinessType());
        entity.setBusinessId(instance.getBusinessId());
        entity.setBusinessCode(instance.getBusinessCode());
        entity.setDefinitionCode(instance.getDefinitionCode());
        entity.setWorkflowInstanceId(instance.getWorkflowInstanceId());
        entity.setTitle(instance.getTitle());
        entity.setInitiatorId(instance.getInitiatorId());
        entity.setInitiatorName(instance.getInitiatorName());
        entity.setStatus(instance.getStatus() == null ? ApprovalStatus.RUNNING.name() : instance.getStatus().name());
        entity.setCurrentNodeKey(instance.getCurrentNodeKey());
        entity.setCurrentNodeName(instance.getCurrentNodeName());
        entity.setCurrentAssignees(instance.getCurrentAssignees());
        entity.setSubmittedAt(instance.getSubmittedAt());
        entity.setFinishedAt(instance.getFinishedAt());
        entity.setBusinessUrl(instance.getBusinessUrl());
        entity.setRemark(instance.getRemark());
    }
}
