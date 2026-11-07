package com.bluewhale.crm.commerce.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.commerce.domain.model.ContractLedger;
import com.bluewhale.crm.commerce.domain.model.ContractQuery;
import com.bluewhale.crm.commerce.domain.repository.ContractLedgerRepository;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.ContractLedgerEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.ContractLedgerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 合同台账仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ContractLedgerRepositoryImpl implements ContractLedgerRepository {

    private final ContractLedgerMapper contractLedgerMapper;

    @Override
    public List<ContractLedger> page(ContractQuery query) {
        Page<ContractLedgerEntity> page = contractLedgerMapper.selectPage(query.toPage(), buildWrapper(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ContractQuery query) {
        return contractLedgerMapper.selectCount(buildWrapper(query));
    }

    @Override
    public ContractLedger findById(Long id) {
        ContractLedgerEntity entity = contractLedgerMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public ContractLedger findByContractId(Long contractId) {
        ContractLedgerEntity entity = contractLedgerMapper.selectOne(new LambdaQueryWrapper<ContractLedgerEntity>()
                .eq(ContractLedgerEntity::getContractId, contractId));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public List<ContractLedger> findExpiring(LocalDate from, LocalDate to, Long ownerId) {
        LambdaQueryWrapper<ContractLedgerEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(ContractLedgerEntity::getExpireDate, from, to);
        wrapper.eq(ownerId != null, ContractLedgerEntity::getOwnerId, ownerId);
        wrapper.orderByAsc(ContractLedgerEntity::getExpireDate);
        return contractLedgerMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ContractLedger> findByCustomer(Long customerId) {
        LambdaQueryWrapper<ContractLedgerEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContractLedgerEntity::getCustomerId, customerId);
        wrapper.orderByDesc(ContractLedgerEntity::getExpireDate);
        return contractLedgerMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Long> findCustomerIds() {
        return contractLedgerMapper.selectList(new LambdaQueryWrapper<ContractLedgerEntity>()
                        .select(ContractLedgerEntity::getCustomerId))
                .stream()
                .map(ContractLedgerEntity::getCustomerId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public void insert(ContractLedger ledger) {
        ContractLedgerEntity entity = toEntity(ledger);
        contractLedgerMapper.insert(entity);
        ledger.setId(entity.getId());
        ledger.setCreateTime(entity.getCreateTime());
        ledger.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void updateProjection(ContractLedger ledger) {
        LambdaUpdateWrapper<ContractLedgerEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ContractLedgerEntity::getId, ledger.getId())
                .set(ContractLedgerEntity::getContractNo, ledger.getContractNo())
                .set(ContractLedgerEntity::getTitle, ledger.getTitle())
                .set(ContractLedgerEntity::getCategory, ledger.getCategory())
                .set(ContractLedgerEntity::getAmount, ledger.getAmount())
                .set(ContractLedgerEntity::getCurrency, ledger.getCurrency())
                .set(ContractLedgerEntity::getStatus, ledger.getStatus())
                .set(ContractLedgerEntity::getEffectiveDate, ledger.getEffectiveDate())
                .set(ContractLedgerEntity::getExpireDate, ledger.getExpireDate())
                .set(ContractLedgerEntity::getLastSyncAt, ledger.getLastSyncAt())
                .set(ContractLedgerEntity::getUpdateTime, LocalDateTime.now());
        contractLedgerMapper.update(null, wrapper);
    }

    @Override
    public boolean updateRenewal(ContractLedger ledger) {
        LambdaUpdateWrapper<ContractLedgerEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ContractLedgerEntity::getId, ledger.getId())
                .set(ContractLedgerEntity::getRenewalStatus, ledger.getRenewalStatus())
                .set(ContractLedgerEntity::getRenewalOwnerId, ledger.getRenewalOwnerId())
                .set(ContractLedgerEntity::getRenewalRemark, ledger.getRenewalRemark())
                .set(ContractLedgerEntity::getRenewalAt, ledger.getRenewalAt())
                .set(ContractLedgerEntity::getUpdateTime, LocalDateTime.now());
        return contractLedgerMapper.update(null, wrapper) > 0;
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<ContractLedgerEntity> buildWrapper(ContractQuery query) {
        LambdaQueryWrapper<ContractLedgerEntity> wrapper = new LambdaQueryWrapper<>();
        String keyword = query.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(inner -> inner.like(ContractLedgerEntity::getContractNo, trimmed)
                    .or().like(ContractLedgerEntity::getTitle, trimmed));
        }
        wrapper.eq(query.getCustomerId() != null, ContractLedgerEntity::getCustomerId, query.getCustomerId());
        wrapper.eq(query.getStatus() != null && !query.getStatus().isBlank(),
                ContractLedgerEntity::getStatus, query.getStatus());
        wrapper.eq(query.getRenewalStatus() != null && !query.getRenewalStatus().isBlank(),
                ContractLedgerEntity::getRenewalStatus, query.getRenewalStatus());
        wrapper.ge(query.getExpireFrom() != null, ContractLedgerEntity::getExpireDate, query.getExpireFrom());
        wrapper.le(query.getExpireTo() != null, ContractLedgerEntity::getExpireDate, query.getExpireTo());
        wrapper.eq(query.getOwnerId() != null, ContractLedgerEntity::getOwnerId, query.getOwnerId());
        wrapper.orderByAsc(ContractLedgerEntity::getExpireDate)
                .orderByDesc(ContractLedgerEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private ContractLedger toDomain(ContractLedgerEntity entity) {
        ContractLedger ledger = new ContractLedger();
        ledger.setId(entity.getId());
        ledger.setContractId(entity.getContractId());
        ledger.setContractNo(entity.getContractNo());
        ledger.setCustomerId(entity.getCustomerId());
        ledger.setTitle(entity.getTitle());
        ledger.setCategory(entity.getCategory());
        ledger.setAmount(entity.getAmount());
        ledger.setCurrency(entity.getCurrency());
        ledger.setStatus(entity.getStatus());
        ledger.setEffectiveDate(entity.getEffectiveDate());
        ledger.setExpireDate(entity.getExpireDate());
        ledger.setBizType(entity.getBizType());
        ledger.setBizId(entity.getBizId());
        ledger.setRenewalStatus(entity.getRenewalStatus());
        ledger.setRenewalOwnerId(entity.getRenewalOwnerId());
        ledger.setRenewalRemark(entity.getRenewalRemark());
        ledger.setRenewalAt(entity.getRenewalAt());
        ledger.setLastSyncAt(entity.getLastSyncAt());
        ledger.setOwnerId(entity.getOwnerId());
        ledger.setDeptId(entity.getDeptId());
        ledger.setRemark(entity.getRemark());
        ledger.setCreateTime(entity.getCreateTime());
        ledger.setUpdateTime(entity.getUpdateTime());
        return ledger;
    }

    /**
     * 领域模型转实体。
     *
     * @param ledger 领域模型
     * @return 实体
     */
    private ContractLedgerEntity toEntity(ContractLedger ledger) {
        ContractLedgerEntity entity = new ContractLedgerEntity();
        entity.setContractId(ledger.getContractId());
        entity.setContractNo(ledger.getContractNo());
        entity.setCustomerId(ledger.getCustomerId());
        entity.setTitle(ledger.getTitle());
        entity.setCategory(ledger.getCategory());
        entity.setAmount(ledger.getAmount());
        entity.setCurrency(ledger.getCurrency());
        entity.setStatus(ledger.getStatus());
        entity.setEffectiveDate(ledger.getEffectiveDate());
        entity.setExpireDate(ledger.getExpireDate());
        entity.setBizType(ledger.getBizType());
        entity.setBizId(ledger.getBizId());
        entity.setRenewalStatus(ledger.getRenewalStatus());
        entity.setRenewalOwnerId(ledger.getRenewalOwnerId());
        entity.setRenewalRemark(ledger.getRenewalRemark());
        entity.setRenewalAt(ledger.getRenewalAt());
        entity.setLastSyncAt(ledger.getLastSyncAt());
        entity.setOwnerId(ledger.getOwnerId());
        entity.setDeptId(ledger.getDeptId());
        entity.setRemark(ledger.getRemark());
        return entity;
    }
}
