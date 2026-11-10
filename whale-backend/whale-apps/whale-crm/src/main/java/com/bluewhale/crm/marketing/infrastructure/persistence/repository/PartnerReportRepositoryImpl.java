package com.bluewhale.crm.marketing.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.marketing.domain.model.PartnerReport;
import com.bluewhale.crm.marketing.domain.model.PartnerReportQuery;
import com.bluewhale.crm.marketing.domain.model.PartnerReportStatus;
import com.bluewhale.crm.marketing.domain.repository.PartnerReportRepository;
import com.bluewhale.crm.marketing.infrastructure.persistence.entity.PartnerReportEntity;
import com.bluewhale.crm.marketing.infrastructure.persistence.mapper.PartnerReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 渠道报单仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PartnerReportRepositoryImpl implements PartnerReportRepository {

    private final PartnerReportMapper partnerReportMapper;

    @Override
    public List<PartnerReport> page(PartnerReportQuery query) {
        Page<PartnerReportEntity> page = new Page<>(query.getCurrent(), query.getSize());
        return partnerReportMapper.selectPage(page, buildWrapper(query)).getRecords().stream()
                .map(this::toDomain).toList();
    }

    @Override
    public long count(PartnerReportQuery query) {
        return partnerReportMapper.selectCount(buildWrapper(query));
    }

    @Override
    public PartnerReport findById(Long id) {
        PartnerReportEntity entity = partnerReportMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void insert(PartnerReport report) {
        PartnerReportEntity entity = toEntity(report);
        partnerReportMapper.insert(entity);
        report.setId(entity.getId());
        report.setCreateTime(entity.getCreateTime());
        report.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(PartnerReport report) {
        partnerReportMapper.updateById(toEntity(report));
    }

    @Override
    public long countByReportNoPrefix(String prefix) {
        return partnerReportMapper.selectCount(new LambdaQueryWrapper<PartnerReportEntity>()
                .likeRight(PartnerReportEntity::getReportNo, prefix));
    }

    @Override
    public boolean existsActiveReport(Long customerId, String projectName, Long excludeId) {
        return partnerReportMapper.selectCount(new LambdaQueryWrapper<PartnerReportEntity>()
                .eq(PartnerReportEntity::getCustomerId, customerId)
                .eq(PartnerReportEntity::getProjectName, projectName)
                .in(PartnerReportEntity::getStatus,
                        PartnerReportStatus.PENDING.name(),
                        PartnerReportStatus.CONFLICT.name(),
                        PartnerReportStatus.APPROVED.name())
                .ne(excludeId != null, PartnerReportEntity::getId, excludeId)) > 0;
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<PartnerReportEntity> buildWrapper(PartnerReportQuery query) {
        LambdaQueryWrapper<PartnerReportEntity> wrapper = new LambdaQueryWrapper<PartnerReportEntity>()
                .eq(StringUtils.hasText(query.getStatus()), PartnerReportEntity::getStatus, query.getStatus())
                .eq(query.getPartnerId() != null, PartnerReportEntity::getPartnerId, query.getPartnerId())
                .eq(query.getCustomerId() != null, PartnerReportEntity::getCustomerId, query.getCustomerId())
                .eq(query.getScopeUserId() != null, PartnerReportEntity::getOwnerId, query.getScopeUserId())
                .orderByDesc(PartnerReportEntity::getCreateTime);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(PartnerReportEntity::getReportNo, keyword)
                    .or().like(PartnerReportEntity::getCustomerName, keyword)
                    .or().like(PartnerReportEntity::getProjectName, keyword));
        }
        return wrapper;
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 报单
     */
    private PartnerReport toDomain(PartnerReportEntity entity) {
        PartnerReport report = new PartnerReport();
        report.setId(entity.getId());
        report.setTenantId(entity.getTenantId());
        report.setReportNo(entity.getReportNo());
        report.setPartnerId(entity.getPartnerId());
        report.setPartnerName(entity.getPartnerName());
        report.setCustomerId(entity.getCustomerId());
        report.setCustomerName(entity.getCustomerName());
        report.setProjectName(entity.getProjectName());
        report.setEstimatedAmount(entity.getEstimatedAmount());
        report.setStatus(PartnerReportStatus.from(entity.getStatus()));
        report.setConflictReason(entity.getConflictReason());
        report.setApprovedAmount(entity.getApprovedAmount());
        report.setResolvedBy(entity.getResolvedBy());
        report.setResolvedAt(entity.getResolvedAt());
        report.setOwnerId(entity.getOwnerId());
        report.setDeptId(entity.getDeptId());
        report.setLockVersion(entity.getLockVersion());
        report.setCreateTime(entity.getCreateTime());
        report.setUpdateTime(entity.getUpdateTime());
        return report;
    }

    /**
     * 领域对象转实体。
     *
     * @param report 报单
     * @return 实体
     */
    private PartnerReportEntity toEntity(PartnerReport report) {
        PartnerReportEntity entity = new PartnerReportEntity();
        entity.setId(report.getId());
        entity.setTenantId(report.getTenantId());
        entity.setReportNo(report.getReportNo());
        entity.setPartnerId(report.getPartnerId());
        entity.setPartnerName(report.getPartnerName());
        entity.setCustomerId(report.getCustomerId());
        entity.setCustomerName(report.getCustomerName());
        entity.setProjectName(report.getProjectName());
        entity.setEstimatedAmount(report.getEstimatedAmount());
        entity.setStatus(report.getStatus() == null ? null : report.getStatus().name());
        entity.setConflictReason(report.getConflictReason());
        entity.setApprovedAmount(report.getApprovedAmount());
        entity.setResolvedBy(report.getResolvedBy());
        entity.setResolvedAt(report.getResolvedAt());
        entity.setOwnerId(report.getOwnerId());
        entity.setDeptId(report.getDeptId());
        entity.setLockVersion(report.getLockVersion());
        return entity;
    }
}
