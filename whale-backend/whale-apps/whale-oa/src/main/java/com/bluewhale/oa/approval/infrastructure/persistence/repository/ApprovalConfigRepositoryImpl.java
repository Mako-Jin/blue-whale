package com.bluewhale.oa.approval.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.oa.approval.domain.model.ApprovalConfig;
import com.bluewhale.oa.approval.domain.model.ApprovalConfigQuery;
import com.bluewhale.oa.approval.domain.repository.ApprovalConfigRepository;
import com.bluewhale.oa.approval.infrastructure.persistence.entity.ApprovalConfigEntity;
import com.bluewhale.oa.approval.infrastructure.persistence.mapper.ApprovalConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 审批配置仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ApprovalConfigRepositoryImpl implements ApprovalConfigRepository {

    private final ApprovalConfigMapper approvalConfigMapper;

    @Override
    public List<ApprovalConfig> page(ApprovalConfigQuery query) {
        Page<ApprovalConfigEntity> page = approvalConfigMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L), false), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ApprovalConfigQuery query) {
        Long total = approvalConfigMapper.selectCount(filter(query));
        return total == null ? 0L : total;
    }

    @Override
    public Optional<ApprovalConfig> findById(Long id) {
        return Optional.ofNullable(approvalConfigMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<ApprovalConfig> findByBusinessType(String businessType) {
        return Optional.ofNullable(approvalConfigMapper.selectOne(new LambdaQueryWrapper<ApprovalConfigEntity>()
                .eq(ApprovalConfigEntity::getBusinessType, businessType)
                .last("limit 1"))).map(this::toDomain);
    }

    @Override
    public void insert(ApprovalConfig config) {
        ApprovalConfigEntity entity = new ApprovalConfigEntity();
        fill(entity, config);
        approvalConfigMapper.insert(entity);
        config.setId(entity.getId());
        config.setCreateTime(entity.getCreateTime());
    }

    @Override
    public void update(ApprovalConfig config) {
        approvalConfigMapper.update(null, new LambdaUpdateWrapper<ApprovalConfigEntity>()
                .eq(ApprovalConfigEntity::getId, config.getId())
                .set(ApprovalConfigEntity::getBusinessName, config.getBusinessName())
                .set(ApprovalConfigEntity::getDefinitionCode, config.getDefinitionCode())
                .set(ApprovalConfigEntity::getTitleTemplate, config.getTitleTemplate())
                .set(ApprovalConfigEntity::getBusinessUrl, config.getBusinessUrl())
                .set(ApprovalConfigEntity::getCcUserIds, config.getCcUserIds())
                .set(ApprovalConfigEntity::getEnabled, config.isEnabled())
                .set(ApprovalConfigEntity::getRemark, config.getRemark()));
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<ApprovalConfigEntity> filter(ApprovalConfigQuery query) {
        return new LambdaQueryWrapper<ApprovalConfigEntity>()
                .like(StringUtils.hasText(query.getBusinessType()),
                        ApprovalConfigEntity::getBusinessType, query.getBusinessType())
                .eq(query.getEnabled() != null, ApprovalConfigEntity::getEnabled, query.getEnabled())
                .orderByDesc(ApprovalConfigEntity::getCreateTime);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private ApprovalConfig toDomain(ApprovalConfigEntity entity) {
        ApprovalConfig config = new ApprovalConfig();
        config.setId(entity.getId());
        config.setBusinessType(entity.getBusinessType());
        config.setBusinessName(entity.getBusinessName());
        config.setDefinitionCode(entity.getDefinitionCode());
        config.setTitleTemplate(entity.getTitleTemplate());
        config.setBusinessUrl(entity.getBusinessUrl());
        config.setCcUserIds(entity.getCcUserIds());
        config.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        config.setRemark(entity.getRemark());
        config.setCreateTime(entity.getCreateTime());
        config.setUpdateTime(entity.getUpdateTime());
        return config;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param config 领域模型
     */
    private void fill(ApprovalConfigEntity entity, ApprovalConfig config) {
        entity.setBusinessType(config.getBusinessType());
        entity.setBusinessName(config.getBusinessName());
        entity.setDefinitionCode(config.getDefinitionCode());
        entity.setTitleTemplate(config.getTitleTemplate());
        entity.setBusinessUrl(config.getBusinessUrl());
        entity.setCcUserIds(config.getCcUserIds());
        entity.setEnabled(config.isEnabled());
        entity.setRemark(config.getRemark());
    }
}
