package com.bluewhale.erp.analytics.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.erp.analytics.domain.model.AnalyticsStatus;
import com.bluewhale.erp.analytics.domain.model.CockpitSnapshot;
import com.bluewhale.erp.analytics.domain.model.CostFact;
import com.bluewhale.erp.analytics.domain.model.QualityFact;
import com.bluewhale.erp.analytics.domain.model.ReceivableFact;
import com.bluewhale.erp.analytics.domain.model.SalesFact;
import com.bluewhale.erp.analytics.domain.model.StockFact;
import com.bluewhale.erp.analytics.domain.repository.AnalyticsRepository;
import com.bluewhale.erp.analytics.infrastructure.persistence.entity.CockpitSnapshotEntity;
import com.bluewhale.erp.analytics.infrastructure.persistence.entity.CostFactEntity;
import com.bluewhale.erp.analytics.infrastructure.persistence.entity.QualityFactEntity;
import com.bluewhale.erp.analytics.infrastructure.persistence.entity.ReceivableFactEntity;
import com.bluewhale.erp.analytics.infrastructure.persistence.entity.SalesFactEntity;
import com.bluewhale.erp.analytics.infrastructure.persistence.entity.StockFactEntity;
import com.bluewhale.erp.analytics.infrastructure.persistence.mapper.CockpitSnapshotMapper;
import com.bluewhale.erp.analytics.infrastructure.persistence.mapper.CostFactMapper;
import com.bluewhale.erp.analytics.infrastructure.persistence.mapper.QualityFactMapper;
import com.bluewhale.erp.analytics.infrastructure.persistence.mapper.ReceivableFactMapper;
import com.bluewhale.erp.analytics.infrastructure.persistence.mapper.SalesFactMapper;
import com.bluewhale.erp.analytics.infrastructure.persistence.mapper.StockFactMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 驾驶舱快照与只读事实仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AnalyticsRepositoryImpl implements AnalyticsRepository {

    /** 快照编号前缀 */
    private static final String SNAPSHOT_PREFIX = "CPS-";

    private final CockpitSnapshotMapper cockpitSnapshotMapper;

    private final SalesFactMapper salesFactMapper;

    private final CostFactMapper costFactMapper;

    private final StockFactMapper stockFactMapper;

    private final ReceivableFactMapper receivableFactMapper;

    private final QualityFactMapper qualityFactMapper;

    @Override
    public Optional<CockpitSnapshot> findActiveSnapshot(String periodCode) {
        List<CockpitSnapshotEntity> list = cockpitSnapshotMapper.selectList(
                new LambdaQueryWrapper<CockpitSnapshotEntity>()
                        .eq(CockpitSnapshotEntity::getPeriodCode, periodCode)
                        .eq(CockpitSnapshotEntity::getStatus, AnalyticsStatus.SNAPSHOT_ACTIVE)
                        .orderByDesc(CockpitSnapshotEntity::getStatDate)
                        .last("limit 1"));
        return list.isEmpty() ? Optional.empty() : Optional.of(toSnapshot(list.get(0)));
    }

    @Override
    public Optional<CockpitSnapshot> findSnapshot(String periodCode, LocalDate statDate) {
        CockpitSnapshotEntity entity = cockpitSnapshotMapper.selectOne(
                new LambdaQueryWrapper<CockpitSnapshotEntity>()
                        .eq(CockpitSnapshotEntity::getPeriodCode, periodCode)
                        .eq(CockpitSnapshotEntity::getStatDate, statDate)
                        .last("limit 1"));
        return entity == null ? Optional.empty() : Optional.of(toSnapshot(entity));
    }

    @Override
    public String nextSnapshotNo(String periodCode) {
        long sequence = cockpitSnapshotMapper.selectCount(new LambdaQueryWrapper<CockpitSnapshotEntity>()
                .likeRight(CockpitSnapshotEntity::getSnapshotNo, SNAPSHOT_PREFIX + periodCode)) + 1;
        return String.format("%s%s-%04d", SNAPSHOT_PREFIX, periodCode, sequence);
    }

    @Override
    public void insertSnapshot(CockpitSnapshot snapshot) {
        CockpitSnapshotEntity entity = new CockpitSnapshotEntity();
        fillSnapshot(entity, snapshot);
        cockpitSnapshotMapper.insert(entity);
        snapshot.setId(entity.getId());
    }

    @Override
    public void updateSnapshot(CockpitSnapshot snapshot) {
        CockpitSnapshotEntity entity = new CockpitSnapshotEntity();
        entity.setId(snapshot.getId());
        fillSnapshot(entity, snapshot);
        cockpitSnapshotMapper.updateById(entity);
    }

    @Override
    public void markPeriodStale(String periodCode) {
        cockpitSnapshotMapper.update(null, new LambdaUpdateWrapper<CockpitSnapshotEntity>()
                .eq(CockpitSnapshotEntity::getPeriodCode, periodCode)
                .eq(CockpitSnapshotEntity::getStatus, AnalyticsStatus.SNAPSHOT_ACTIVE)
                .set(CockpitSnapshotEntity::getStatus, AnalyticsStatus.SNAPSHOT_STALE));
    }

    @Override
    public List<SalesFact> listSalesFacts(LocalDate from, LocalDate to) {
        LambdaQueryWrapper<SalesFactEntity> wrapper = new LambdaQueryWrapper<>();
        if (from != null) {
            wrapper.ge(SalesFactEntity::getDocDate, from);
        }
        if (to != null) {
            wrapper.le(SalesFactEntity::getDocDate, to);
        }
        return salesFactMapper.selectList(wrapper).stream().map(this::toSalesFact).toList();
    }

    @Override
    public List<CostFact> listCostFacts(LocalDate from, LocalDate to) {
        LambdaQueryWrapper<CostFactEntity> wrapper = new LambdaQueryWrapper<>();
        if (from != null) {
            wrapper.ge(CostFactEntity::getOccurredDate, from);
        }
        if (to != null) {
            wrapper.le(CostFactEntity::getOccurredDate, to);
        }
        return costFactMapper.selectList(wrapper).stream().map(this::toCostFact).toList();
    }

    @Override
    public List<StockFact> listStockFacts() {
        return stockFactMapper.selectList(new LambdaQueryWrapper<>()).stream().map(this::toStockFact).toList();
    }

    @Override
    public List<ReceivableFact> listReceivableFacts() {
        return receivableFactMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .map(this::toReceivableFact).toList();
    }

    @Override
    public List<QualityFact> listQualityFacts() {
        return qualityFactMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .map(this::toQualityFact).toList();
    }

    /**
     * 领域快照写入实体。
     *
     * @param entity 实体
     * @param snapshot 快照
     */
    private void fillSnapshot(CockpitSnapshotEntity entity, CockpitSnapshot snapshot) {
        entity.setSnapshotNo(snapshot.getSnapshotNo());
        entity.setPeriodCode(snapshot.getPeriodCode());
        entity.setStatDate(snapshot.getStatDate());
        entity.setRevenueAmount(snapshot.getRevenueAmount());
        entity.setCostAmount(snapshot.getCostAmount());
        entity.setProfitAmount(snapshot.getProfitAmount());
        entity.setOrderCount(snapshot.getOrderCount());
        entity.setAlertCount(snapshot.getAlertCount());
        entity.setStatus(snapshot.getStatus());
        entity.setRefreshedAt(snapshot.getRefreshedAt());
        entity.setRemark(snapshot.getRemark());
    }

    /**
     * 快照实体转领域模型。
     *
     * @param entity 实体
     * @return 快照
     */
    private CockpitSnapshot toSnapshot(CockpitSnapshotEntity entity) {
        CockpitSnapshot snapshot = new CockpitSnapshot();
        snapshot.setId(entity.getId());
        snapshot.setSnapshotNo(entity.getSnapshotNo());
        snapshot.setPeriodCode(entity.getPeriodCode());
        snapshot.setStatDate(entity.getStatDate());
        snapshot.setRevenueAmount(entity.getRevenueAmount());
        snapshot.setCostAmount(entity.getCostAmount());
        snapshot.setProfitAmount(entity.getProfitAmount());
        snapshot.setOrderCount(entity.getOrderCount());
        snapshot.setAlertCount(entity.getAlertCount());
        snapshot.setStatus(entity.getStatus());
        snapshot.setRefreshedAt(entity.getRefreshedAt());
        snapshot.setRemark(entity.getRemark());
        return snapshot;
    }

    /**
     * 销售事实转领域模型。
     *
     * @param entity 实体
     * @return 事实
     */
    private SalesFact toSalesFact(SalesFactEntity entity) {
        SalesFact fact = new SalesFact();
        fact.setDocNo(entity.getDocNo());
        fact.setDocDate(entity.getDocDate());
        fact.setCustomerId(entity.getCustomerId());
        fact.setCustomerName(entity.getCustomerName());
        fact.setItemId(entity.getItemId());
        fact.setItemCode(entity.getItemCode());
        fact.setItemName(entity.getItemName());
        fact.setWarehouseId(entity.getWarehouseId());
        fact.setQuantity(entity.getQuantity());
        fact.setShippedQuantity(entity.getShippedQuantity());
        fact.setAmount(entity.getAmount());
        return fact;
    }

    /**
     * 成本事实转领域模型。
     *
     * @param entity 实体
     * @return 事实
     */
    private CostFact toCostFact(CostFactEntity entity) {
        CostFact fact = new CostFact();
        fact.setItemId(entity.getItemId());
        fact.setItemCode(entity.getItemCode());
        fact.setItemName(entity.getItemName());
        fact.setWarehouseId(entity.getWarehouseId());
        fact.setOccurredDate(entity.getOccurredDate());
        fact.setQuantity(entity.getQuantity());
        fact.setAmount(entity.getAmount());
        return fact;
    }

    /**
     * 库存事实转领域模型。
     *
     * @param entity 实体
     * @return 事实
     */
    private StockFact toStockFact(StockFactEntity entity) {
        StockFact fact = new StockFact();
        fact.setItemId(entity.getItemId());
        fact.setItemCode(entity.getItemCode());
        fact.setItemName(entity.getItemName());
        fact.setWarehouseId(entity.getWarehouseId());
        fact.setQuantity(entity.getQuantity());
        fact.setAmount(entity.getAmount());
        return fact;
    }

    /**
     * 应收事实转领域模型。
     *
     * @param entity 实体
     * @return 事实
     */
    private ReceivableFact toReceivableFact(ReceivableFactEntity entity) {
        ReceivableFact fact = new ReceivableFact();
        fact.setDocNo(entity.getDocNo());
        fact.setCustomerName(entity.getCustomerName());
        fact.setDocDate(entity.getDocDate());
        fact.setDueDate(entity.getDueDate());
        fact.setAmount(entity.getAmount());
        fact.setSettledAmount(entity.getSettledAmount());
        fact.setBadDebtAmount(entity.getBadDebtAmount());
        fact.setStatus(entity.getStatus());
        return fact;
    }

    /**
     * 质量事实转领域模型。
     *
     * @param entity 实体
     * @return 事实
     */
    private QualityFact toQualityFact(QualityFactEntity entity) {
        QualityFact fact = new QualityFact();
        fact.setNcrNo(entity.getNcrNo());
        fact.setSourceNo(entity.getSourceNo());
        fact.setItemId(entity.getItemId());
        fact.setItemCode(entity.getItemCode());
        fact.setItemName(entity.getItemName());
        fact.setQuantity(entity.getQuantity());
        fact.setStatus(entity.getStatus());
        fact.setCostAmount(entity.getCostAmount());
        fact.setCreatedAt(entity.getCreatedAt());
        return fact;
    }
}
