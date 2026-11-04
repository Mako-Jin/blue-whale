package com.bluewhale.erp.finance.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.finance.domain.model.ArApQuery;
import com.bluewhale.erp.finance.domain.model.FinanceStatus;
import com.bluewhale.erp.finance.domain.model.Receivable;
import com.bluewhale.erp.finance.domain.model.ReceivableSettlement;
import com.bluewhale.erp.finance.domain.repository.ReceivableRepository;
import com.bluewhale.erp.finance.infrastructure.persistence.entity.ReceivableEntity;
import com.bluewhale.erp.finance.infrastructure.persistence.entity.ReceivableSettlementEntity;
import com.bluewhale.erp.finance.infrastructure.persistence.mapper.ReceivableMapper;
import com.bluewhale.erp.finance.infrastructure.persistence.mapper.ReceivableSettlementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 应收台账仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ReceivableRepositoryImpl implements ReceivableRepository {

    private final ReceivableMapper receivableMapper;

    private final ReceivableSettlementMapper settlementMapper;

    @Override
    public List<Receivable> page(ArApQuery query) {
        Page<ReceivableEntity> page = receivableMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ArApQuery query) {
        return receivableMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Receivable> findById(Long id) {
        return Optional.ofNullable(receivableMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Receivable> findByDocNo(String docNo) {
        return Optional.ofNullable(receivableMapper.selectOne(new LambdaQueryWrapper<ReceivableEntity>()
                .eq(ReceivableEntity::getDocNo, docNo))).map(this::toDomain);
    }

    @Override
    public Optional<Receivable> findBySource(String sourceType, String sourceId) {
        if (!StringUtils.hasText(sourceType) || !StringUtils.hasText(sourceId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(receivableMapper.selectOne(new LambdaQueryWrapper<ReceivableEntity>()
                .eq(ReceivableEntity::getSourceType, sourceType)
                .eq(ReceivableEntity::getSourceId, sourceId)
                .last("limit 1"))).map(this::toDomain);
    }

    @Override
    public void insert(Receivable receivable) {
        ReceivableEntity entity = new ReceivableEntity();
        fill(entity, receivable);
        receivableMapper.insert(entity);
        receivable.setId(entity.getId());
    }

    @Override
    public void update(Receivable receivable) {
        ReceivableEntity entity = new ReceivableEntity();
        entity.setId(receivable.getId());
        entity.setSettledAmount(receivable.getSettledAmount());
        entity.setBadDebtAmount(receivable.getBadDebtAmount());
        entity.setStatus(receivable.getStatus());
        receivableMapper.updateById(entity);
    }

    @Override
    public void insertSettlement(ReceivableSettlement settlement) {
        ReceivableSettlementEntity entity = new ReceivableSettlementEntity();
        entity.setReceivableId(settlement.getReceivableId());
        entity.setSettleDate(settlement.getSettleDate());
        entity.setAmount(settlement.getAmount());
        entity.setSettleType(settlement.getSettleType());
        entity.setReferenceNo(settlement.getReferenceNo());
        entity.setRemark(settlement.getRemark());
        settlementMapper.insert(entity);
        settlement.setId(entity.getId());
    }

    @Override
    public List<Receivable> listOutstanding() {
        return receivableMapper.selectList(new LambdaQueryWrapper<ReceivableEntity>()
                        .in(ReceivableEntity::getStatus,
                                FinanceStatus.LEDGER_OPEN, FinanceStatus.LEDGER_PARTIALLY_SETTLED)
                        .orderByAsc(ReceivableEntity::getDueDate))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<ReceivableEntity> filter(ArApQuery query) {
        LambdaQueryWrapper<ReceivableEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(ReceivableEntity::getDocNo, query.getKeyword())
                    .or().like(ReceivableEntity::getCustomerName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(ReceivableEntity::getStatus, query.getStatus());
        }
        if (query.getPartyId() != null) {
            wrapper.eq(ReceivableEntity::getCustomerId, query.getPartyId());
        }
        return wrapper.orderByDesc(ReceivableEntity::getDocDate);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Receivable toDomain(ReceivableEntity entity) {
        Receivable receivable = new Receivable();
        receivable.setId(entity.getId());
        receivable.setDocNo(entity.getDocNo());
        receivable.setCustomerId(entity.getCustomerId());
        receivable.setCustomerName(entity.getCustomerName());
        receivable.setSourceType(entity.getSourceType());
        receivable.setSourceId(entity.getSourceId());
        receivable.setDocDate(entity.getDocDate());
        receivable.setDueDate(entity.getDueDate());
        receivable.setCurrency(entity.getCurrency());
        receivable.setAmount(entity.getAmount());
        receivable.setSettledAmount(entity.getSettledAmount());
        receivable.setBadDebtAmount(entity.getBadDebtAmount());
        receivable.setStatus(entity.getStatus());
        receivable.setRemark(entity.getRemark());
        return receivable;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param receivable 领域模型
     */
    private void fill(ReceivableEntity entity, Receivable receivable) {
        entity.setDocNo(receivable.getDocNo());
        entity.setCustomerId(receivable.getCustomerId());
        entity.setCustomerName(receivable.getCustomerName());
        entity.setSourceType(receivable.getSourceType());
        entity.setSourceId(receivable.getSourceId());
        entity.setDocDate(receivable.getDocDate());
        entity.setDueDate(receivable.getDueDate());
        entity.setCurrency(receivable.getCurrency());
        entity.setAmount(receivable.getAmount());
        entity.setSettledAmount(receivable.getSettledAmount());
        entity.setBadDebtAmount(receivable.getBadDebtAmount());
        entity.setStatus(receivable.getStatus());
        entity.setRemark(receivable.getRemark());
    }
}
