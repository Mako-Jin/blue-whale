package com.bluewhale.erp.finance.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.finance.domain.model.ArApQuery;
import com.bluewhale.erp.finance.domain.model.FinanceStatus;
import com.bluewhale.erp.finance.domain.model.Payable;
import com.bluewhale.erp.finance.domain.model.PayableSettlement;
import com.bluewhale.erp.finance.domain.repository.PayableRepository;
import com.bluewhale.erp.finance.infrastructure.persistence.entity.PayableEntity;
import com.bluewhale.erp.finance.infrastructure.persistence.entity.PayableSettlementEntity;
import com.bluewhale.erp.finance.infrastructure.persistence.mapper.PayableMapper;
import com.bluewhale.erp.finance.infrastructure.persistence.mapper.PayableSettlementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 应付台账仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PayableRepositoryImpl implements PayableRepository {

    private final PayableMapper payableMapper;

    private final PayableSettlementMapper settlementMapper;

    @Override
    public List<Payable> page(ArApQuery query) {
        Page<PayableEntity> page = payableMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ArApQuery query) {
        return payableMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Payable> findById(Long id) {
        return Optional.ofNullable(payableMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Payable> findByDocNo(String docNo) {
        return Optional.ofNullable(payableMapper.selectOne(new LambdaQueryWrapper<PayableEntity>()
                .eq(PayableEntity::getDocNo, docNo))).map(this::toDomain);
    }

    @Override
    public Optional<Payable> findBySource(String sourceType, String sourceId) {
        if (!StringUtils.hasText(sourceType) || !StringUtils.hasText(sourceId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(payableMapper.selectOne(new LambdaQueryWrapper<PayableEntity>()
                .eq(PayableEntity::getSourceType, sourceType)
                .eq(PayableEntity::getSourceId, sourceId)
                .last("limit 1"))).map(this::toDomain);
    }

    @Override
    public void insert(Payable payable) {
        PayableEntity entity = new PayableEntity();
        fill(entity, payable);
        payableMapper.insert(entity);
        payable.setId(entity.getId());
    }

    @Override
    public void update(Payable payable) {
        PayableEntity entity = new PayableEntity();
        entity.setId(payable.getId());
        entity.setSettledAmount(payable.getSettledAmount());
        entity.setStatus(payable.getStatus());
        payableMapper.updateById(entity);
    }

    @Override
    public void insertSettlement(PayableSettlement settlement) {
        PayableSettlementEntity entity = new PayableSettlementEntity();
        entity.setPayableId(settlement.getPayableId());
        entity.setSettleDate(settlement.getSettleDate());
        entity.setAmount(settlement.getAmount());
        entity.setSettleType(settlement.getSettleType());
        entity.setReferenceNo(settlement.getReferenceNo());
        entity.setRemark(settlement.getRemark());
        settlementMapper.insert(entity);
        settlement.setId(entity.getId());
    }

    @Override
    public List<Payable> listOutstanding() {
        return payableMapper.selectList(new LambdaQueryWrapper<PayableEntity>()
                        .in(PayableEntity::getStatus,
                                FinanceStatus.LEDGER_OPEN, FinanceStatus.LEDGER_PARTIALLY_SETTLED)
                        .orderByAsc(PayableEntity::getDueDate))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<PayableEntity> filter(ArApQuery query) {
        LambdaQueryWrapper<PayableEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(PayableEntity::getDocNo, query.getKeyword())
                    .or().like(PayableEntity::getSupplierName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PayableEntity::getStatus, query.getStatus());
        }
        if (query.getPartyId() != null) {
            wrapper.eq(PayableEntity::getSupplierId, query.getPartyId());
        }
        return wrapper.orderByDesc(PayableEntity::getDocDate);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Payable toDomain(PayableEntity entity) {
        Payable payable = new Payable();
        payable.setId(entity.getId());
        payable.setDocNo(entity.getDocNo());
        payable.setSupplierId(entity.getSupplierId());
        payable.setSupplierName(entity.getSupplierName());
        payable.setSourceType(entity.getSourceType());
        payable.setSourceId(entity.getSourceId());
        payable.setDocDate(entity.getDocDate());
        payable.setDueDate(entity.getDueDate());
        payable.setCurrency(entity.getCurrency());
        payable.setAmount(entity.getAmount());
        payable.setSettledAmount(entity.getSettledAmount());
        payable.setStatus(entity.getStatus());
        payable.setRemark(entity.getRemark());
        return payable;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param payable 领域模型
     */
    private void fill(PayableEntity entity, Payable payable) {
        entity.setDocNo(payable.getDocNo());
        entity.setSupplierId(payable.getSupplierId());
        entity.setSupplierName(payable.getSupplierName());
        entity.setSourceType(payable.getSourceType());
        entity.setSourceId(payable.getSourceId());
        entity.setDocDate(payable.getDocDate());
        entity.setDueDate(payable.getDueDate());
        entity.setCurrency(payable.getCurrency());
        entity.setAmount(payable.getAmount());
        entity.setSettledAmount(payable.getSettledAmount());
        entity.setStatus(payable.getStatus());
        entity.setRemark(payable.getRemark());
    }
}
