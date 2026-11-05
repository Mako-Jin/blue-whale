package com.bluewhale.erp.scm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.model.StockMovement;
import com.bluewhale.erp.scm.domain.repository.StockRepository;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.StockBalanceEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.StockMovementEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.StockBalanceMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.StockMovementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 库存仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class StockRepositoryImpl implements StockRepository {

    private final StockBalanceMapper stockBalanceMapper;

    private final StockMovementMapper stockMovementMapper;

    @Override
    public List<StockBalance> pageBalances(ScmQuery query) {
        Page<StockBalanceEntity> page = stockBalanceMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), balanceFilter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countBalances(ScmQuery query) {
        return stockBalanceMapper.selectCount(balanceFilter(query));
    }

    @Override
    public List<StockBalance> listBalances(ScmQuery query) {
        return stockBalanceMapper.selectList(balanceFilter(query)).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<LocalDateTime> lastOutboundAt(Long itemId, Long warehouseId) {
        return stockMovementMapper.selectList(new LambdaQueryWrapper<StockMovementEntity>()
                        .eq(StockMovementEntity::getItemId, itemId)
                        .eq(StockMovementEntity::getWarehouseId, warehouseId)
                        .eq(StockMovementEntity::getDirection, ScmStatus.OUT)
                        .orderByDesc(StockMovementEntity::getOccurredAt)
                        .last("limit 1")).stream()
                .findFirst()
                .map(StockMovementEntity::getOccurredAt);
    }

    @Override
    public List<StockMovement> pageMovements(ScmQuery query) {
        Page<StockMovementEntity> page = stockMovementMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), movementFilter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countMovements(ScmQuery query) {
        return stockMovementMapper.selectCount(movementFilter(query));
    }

    @Override
    public Optional<StockBalance> findBalance(Long itemId, Long warehouseId) {
        return Optional.ofNullable(stockBalanceMapper.selectOne(new LambdaQueryWrapper<StockBalanceEntity>()
                .eq(StockBalanceEntity::getItemId, itemId)
                .eq(StockBalanceEntity::getWarehouseId, warehouseId)
                .last("limit 1"))).map(this::toDomain);
    }

    @Override
    public void insertMovement(StockMovement movement) {
        StockMovementEntity entity = new StockMovementEntity();
        entity.setDocNo(movement.getDocNo());
        entity.setSourceType(movement.getSourceType());
        entity.setSourceId(movement.getSourceId());
        entity.setItemId(movement.getItemId());
        entity.setItemCode(movement.getItemCode());
        entity.setItemName(movement.getItemName());
        entity.setWarehouseId(movement.getWarehouseId());
        entity.setDirection(movement.getDirection());
        entity.setQuantity(movement.getQuantity());
        entity.setUnitCost(movement.getUnitCost());
        entity.setAmount(movement.getAmount());
        entity.setOccurredAt(movement.getOccurredAt());
        entity.setRemark(movement.getRemark());
        stockMovementMapper.insert(entity);
        movement.setId(entity.getId());
    }

    @Override
    public void saveBalance(StockBalance balance) {
        StockBalanceEntity entity = new StockBalanceEntity();
        entity.setId(balance.getId());
        entity.setItemId(balance.getItemId());
        entity.setItemCode(balance.getItemCode());
        entity.setItemName(balance.getItemName());
        entity.setWarehouseId(balance.getWarehouseId());
        entity.setQuantity(balance.getQuantity());
        entity.setAmount(balance.getAmount());
        if (balance.getId() == null) {
            stockBalanceMapper.insert(entity);
            balance.setId(entity.getId());
        } else {
            stockBalanceMapper.updateById(entity);
        }
    }

    /**
     * 构建余额查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<StockBalanceEntity> balanceFilter(ScmQuery query) {
        LambdaQueryWrapper<StockBalanceEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(StockBalanceEntity::getItemCode, query.getKeyword())
                    .or().like(StockBalanceEntity::getItemName, query.getKeyword()));
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(StockBalanceEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getItemId() != null) {
            wrapper.eq(StockBalanceEntity::getItemId, query.getItemId());
        }
        return wrapper.orderByAsc(StockBalanceEntity::getItemCode);
    }

    /**
     * 构建流水查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<StockMovementEntity> movementFilter(ScmQuery query) {
        LambdaQueryWrapper<StockMovementEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(StockMovementEntity::getDocNo, query.getKeyword())
                    .or().like(StockMovementEntity::getItemCode, query.getKeyword())
                    .or().like(StockMovementEntity::getItemName, query.getKeyword()));
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(StockMovementEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getItemId() != null) {
            wrapper.eq(StockMovementEntity::getItemId, query.getItemId());
        }
        return wrapper.orderByDesc(StockMovementEntity::getOccurredAt)
                .orderByDesc(StockMovementEntity::getId);
    }

    /**
     * 余额实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private StockBalance toDomain(StockBalanceEntity entity) {
        StockBalance balance = new StockBalance();
        balance.setId(entity.getId());
        balance.setItemId(entity.getItemId());
        balance.setItemCode(entity.getItemCode());
        balance.setItemName(entity.getItemName());
        balance.setWarehouseId(entity.getWarehouseId());
        balance.setQuantity(entity.getQuantity());
        balance.setAmount(entity.getAmount());
        return balance;
    }

    /**
     * 流水实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private StockMovement toDomain(StockMovementEntity entity) {
        StockMovement movement = new StockMovement();
        movement.setId(entity.getId());
        movement.setDocNo(entity.getDocNo());
        movement.setSourceType(entity.getSourceType());
        movement.setSourceId(entity.getSourceId());
        movement.setItemId(entity.getItemId());
        movement.setItemCode(entity.getItemCode());
        movement.setItemName(entity.getItemName());
        movement.setWarehouseId(entity.getWarehouseId());
        movement.setDirection(entity.getDirection());
        movement.setQuantity(entity.getQuantity());
        movement.setUnitCost(entity.getUnitCost());
        movement.setAmount(entity.getAmount());
        movement.setOccurredAt(entity.getOccurredAt());
        movement.setRemark(entity.getRemark());
        return movement;
    }
}
