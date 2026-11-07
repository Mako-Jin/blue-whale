package com.bluewhale.crm.marketing.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.marketing.domain.model.NurtureQuery;
import com.bluewhale.crm.marketing.domain.model.NurtureStatus;
import com.bluewhale.crm.marketing.domain.model.NurtureTask;
import com.bluewhale.crm.marketing.domain.repository.NurtureTaskRepository;
import com.bluewhale.crm.marketing.infrastructure.persistence.entity.NurtureTaskEntity;
import com.bluewhale.crm.marketing.infrastructure.persistence.mapper.NurtureTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 客户培育任务仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class NurtureTaskRepositoryImpl implements NurtureTaskRepository {

    private final NurtureTaskMapper nurtureTaskMapper;

    @Override
    public List<NurtureTask> page(NurtureQuery query) {
        Page<NurtureTaskEntity> page = new Page<>(query.getCurrent(), query.getSize());
        return nurtureTaskMapper.selectPage(page, buildWrapper(query)).getRecords().stream()
                .map(this::toDomain).toList();
    }

    @Override
    public long count(NurtureQuery query) {
        return nurtureTaskMapper.selectCount(buildWrapper(query));
    }

    @Override
    public NurtureTask findById(Long id) {
        NurtureTaskEntity entity = nurtureTaskMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void insert(NurtureTask task) {
        NurtureTaskEntity entity = toEntity(task);
        nurtureTaskMapper.insert(entity);
        task.setId(entity.getId());
        task.setCreateTime(entity.getCreateTime());
        task.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(NurtureTask task) {
        nurtureTaskMapper.updateById(toEntity(task));
    }

    @Override
    public long countByTaskNoPrefix(String prefix) {
        return nurtureTaskMapper.selectCount(new LambdaQueryWrapper<NurtureTaskEntity>()
                .likeRight(NurtureTaskEntity::getTaskNo, prefix));
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<NurtureTaskEntity> buildWrapper(NurtureQuery query) {
        LambdaQueryWrapper<NurtureTaskEntity> wrapper = new LambdaQueryWrapper<NurtureTaskEntity>()
                .eq(StringUtils.hasText(query.getStatus()), NurtureTaskEntity::getStatus, query.getStatus())
                .eq(query.getCustomerId() != null, NurtureTaskEntity::getCustomerId, query.getCustomerId())
                .eq(query.getAssigneeId() != null, NurtureTaskEntity::getAssigneeId, query.getAssigneeId())
                .ge(query.getPlanFrom() != null, NurtureTaskEntity::getPlanDate, query.getPlanFrom())
                .le(query.getPlanTo() != null, NurtureTaskEntity::getPlanDate, query.getPlanTo())
                .eq(query.getScopeUserId() != null, NurtureTaskEntity::getOwnerId, query.getScopeUserId())
                .orderByDesc(NurtureTaskEntity::getCreateTime);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(NurtureTaskEntity::getTaskNo, keyword)
                    .or().like(NurtureTaskEntity::getCustomerName, keyword));
        }
        return wrapper;
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 任务
     */
    private NurtureTask toDomain(NurtureTaskEntity entity) {
        NurtureTask task = new NurtureTask();
        task.setId(entity.getId());
        task.setTenantId(entity.getTenantId());
        task.setTaskNo(entity.getTaskNo());
        task.setCustomerId(entity.getCustomerId());
        task.setCustomerName(entity.getCustomerName());
        task.setLeadId(entity.getLeadId());
        task.setContentDocId(entity.getContentDocId());
        task.setContentDocVersion(entity.getContentDocVersion());
        task.setChannel(entity.getChannel());
        task.setPlanDate(entity.getPlanDate());
        task.setStatus(NurtureStatus.from(entity.getStatus()));
        task.setAssigneeId(entity.getAssigneeId());
        task.setAssigneeName(entity.getAssigneeName());
        task.setNote(entity.getNote());
        task.setOwnerId(entity.getOwnerId());
        task.setDeptId(entity.getDeptId());
        task.setLockVersion(entity.getLockVersion());
        task.setCreateTime(entity.getCreateTime());
        task.setUpdateTime(entity.getUpdateTime());
        return task;
    }

    /**
     * 领域对象转实体。
     *
     * @param task 任务
     * @return 实体
     */
    private NurtureTaskEntity toEntity(NurtureTask task) {
        NurtureTaskEntity entity = new NurtureTaskEntity();
        entity.setId(task.getId());
        entity.setTenantId(task.getTenantId());
        entity.setTaskNo(task.getTaskNo());
        entity.setCustomerId(task.getCustomerId());
        entity.setCustomerName(task.getCustomerName());
        entity.setLeadId(task.getLeadId());
        entity.setContentDocId(task.getContentDocId());
        entity.setContentDocVersion(task.getContentDocVersion());
        entity.setChannel(task.getChannel());
        entity.setPlanDate(task.getPlanDate());
        entity.setStatus(task.getStatus() == null ? null : task.getStatus().name());
        entity.setAssigneeId(task.getAssigneeId());
        entity.setAssigneeName(task.getAssigneeName());
        entity.setNote(task.getNote());
        entity.setOwnerId(task.getOwnerId());
        entity.setDeptId(task.getDeptId());
        entity.setLockVersion(task.getLockVersion());
        return entity;
    }
}
