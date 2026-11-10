package com.bluewhale.erp.scm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.scm.domain.model.SafetyStock;
import com.bluewhale.erp.scm.domain.model.SafetyStockQuery;
import com.bluewhale.erp.scm.domain.repository.SafetyStockRepository;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.SafetyStockEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.SafetyStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 安全库存仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SafetyStockRepositoryImpl implements SafetyStockRepository {

    private final SafetyStockMapper safetyStockMapper;

    @Override
    public List<SafetyStock> page(SafetyStockQuery query) {
        Page<SafetyStockEntity> page = safetyStockMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(SafetyStockQuery query) {
        return safetyStockMapper.selectCount(filter(query));
    }

    @Override
    public List<SafetyStock> list(SafetyStockQuery query) {
        return safetyStockMapper.selectList(filter(query)).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<SafetyStock> findById(Long id) {
        return Optional.ofNullable(safetyStockMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<SafetyStock> find(Long warehouseId, Long itemId) {
        SafetyStockEntity entity = safetyStockMapper.selectOne(new LambdaQueryWrapper<SafetyStockEntity>()
                .eq(SafetyStockEntity::getWarehouseId, warehouseId)
                .eq(SafetyStockEntity::getItemId, itemId)
                .last("limit 1"));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public void insert(SafetyStock safetyStock) {
        SafetyStockEntity entity = new SafetyStockEntity();
        fill(entity, safetyStock);
        safetyStockMapper.insert(entity);
        safetyStock.setId(entity.getId());
    }

    @Override
    public void update(SafetyStock safetyStock) {
        SafetyStockEntity entity = new SafetyStockEntity();
        entity.setId(safetyStock.getId());
        entity.setSafetyQty(safetyStock.getSafetyQty());
        entity.setMaxQty(safetyStock.getMaxQty());
        entity.setRemark(safetyStock.getRemark());
        safetyStockMapper.updateById(entity);
    }

    @Override
    public void deleteById(Long id) {
        safetyStockMapper.deleteById(id);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<SafetyStockEntity> filter(SafetyStockQuery query) {
        LambdaQueryWrapper<SafetyStockEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(SafetyStockEntity::getItemCode, query.getKeyword())
                    .or().like(SafetyStockEntity::getItemName, query.getKeyword()));
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(SafetyStockEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getItemId() != null) {
            wrapper.eq(SafetyStockEntity::getItemId, query.getItemId());
        }
        return wrapper.orderByDesc(SafetyStockEntity::getCreateTime)
                .orderByDesc(SafetyStockEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private SafetyStock toDomain(SafetyStockEntity entity) {
        SafetyStock safetyStock = new SafetyStock();
        safetyStock.setId(entity.getId());
        safetyStock.setWarehouseId(entity.getWarehouseId());
        safetyStock.setItemId(entity.getItemId());
        safetyStock.setItemCode(entity.getItemCode());
        safetyStock.setItemName(entity.getItemName());
        safetyStock.setSafetyQty(entity.getSafetyQty());
        safetyStock.setMaxQty(entity.getMaxQty());
        safetyStock.setRemark(entity.getRemark());
        return safetyStock;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param safetyStock 领域模型
     */
    private void fill(SafetyStockEntity entity, SafetyStock safetyStock) {
        entity.setWarehouseId(safetyStock.getWarehouseId());
        entity.setItemId(safetyStock.getItemId());
        entity.setItemCode(safetyStock.getItemCode());
        entity.setItemName(safetyStock.getItemName());
        entity.setSafetyQty(safetyStock.getSafetyQty());
        entity.setMaxQty(safetyStock.getMaxQty());
        entity.setRemark(safetyStock.getRemark());
    }
}
