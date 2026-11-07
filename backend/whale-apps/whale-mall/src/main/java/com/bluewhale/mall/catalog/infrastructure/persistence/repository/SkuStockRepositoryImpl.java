package com.bluewhale.mall.catalog.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.mall.catalog.domain.model.SkuStock;
import com.bluewhale.mall.catalog.domain.repository.SkuStockRepository;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.SkuStockEntity;
import com.bluewhale.mall.catalog.infrastructure.persistence.mapper.SkuStockMapper;
import com.bluewhale.mall.common.MallErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * SKU 库存仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SkuStockRepositoryImpl implements SkuStockRepository {

    private final SkuStockMapper skuStockMapper;

    @Override
    public Optional<SkuStock> findBySkuId(Long skuId) {
        return Optional.ofNullable(skuStockMapper.selectOne(new LambdaQueryWrapper<SkuStockEntity>()
                .eq(SkuStockEntity::getSkuId, skuId))).map(this::toDomain);
    }

    @Override
    public List<SkuStock> findBySkuIds(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return skuStockMapper.selectList(new LambdaQueryWrapper<SkuStockEntity>()
                        .in(SkuStockEntity::getSkuId, skuIds))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<SkuStock> page(Collection<Long> skuIds, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        LambdaQueryWrapper<SkuStockEntity> wrapper = new LambdaQueryWrapper<>();
        if (skuIds != null && !skuIds.isEmpty()) {
            wrapper.in(SkuStockEntity::getSkuId, skuIds);
        }
        wrapper.orderByAsc(SkuStockEntity::getAvailableQty).orderByDesc(SkuStockEntity::getId);
        Page<SkuStockEntity> page = skuStockMapper.selectPage(new Page<>(current, Math.max(size, 1)), wrapper);
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(Collection<Long> skuIds) {
        LambdaQueryWrapper<SkuStockEntity> wrapper = new LambdaQueryWrapper<>();
        if (skuIds != null && !skuIds.isEmpty()) {
            wrapper.in(SkuStockEntity::getSkuId, skuIds);
        }
        return skuStockMapper.selectCount(wrapper);
    }

    @Override
    public void init(Long skuId) {
        if (skuId == null || findBySkuId(skuId).isPresent()) {
            return;
        }
        SkuStockEntity entity = new SkuStockEntity();
        entity.setSkuId(skuId);
        entity.setAvailableQty(0);
        entity.setReservedQty(0);
        try {
            skuStockMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            // 并发下已被他线程初始化，忽略即可（库存行只需存在一份）
        }
    }

    @Override
    public boolean reserve(Long skuId, int quantity) {
        SkuStock.validateQuantity(quantity);
        return skuStockMapper.reserve(skuId, quantity) > 0;
    }

    @Override
    public boolean release(Long skuId, int quantity) {
        SkuStock.validateQuantity(quantity);
        int affected = skuStockMapper.release(skuId, quantity);
        if (affected <= 0) {
            throw MallErrorCode.exception(MallErrorCode.STOCK_INVALID,
                    "释放预占失败：SKU " + skuId + " 的预占数量不足，库存数据可能已不一致");
        }
        return true;
    }

    @Override
    public boolean ship(Long skuId, int quantity) {
        SkuStock.validateQuantity(quantity);
        int affected = skuStockMapper.ship(skuId, quantity);
        if (affected <= 0) {
            throw MallErrorCode.exception(MallErrorCode.STOCK_INVALID,
                    "出库失败：SKU " + skuId + " 的预占数量不足，库存数据可能已不一致");
        }
        return true;
    }

    @Override
    public boolean unship(Long skuId, int quantity) {
        SkuStock.validateQuantity(quantity);
        int affected = skuStockMapper.unship(skuId, quantity);
        if (affected <= 0) {
            throw MallErrorCode.exception(MallErrorCode.STOCK_NOT_FOUND,
                    "撤回发货失败：SKU " + skuId + " 的库存记录不存在");
        }
        return true;
    }

    @Override
    public int adjust(Long skuId, int delta) {
        if (delta == 0) {
            return availableOf(skuId);
        }
        if (skuStockMapper.adjust(skuId, delta) > 0) {
            return availableOf(skuId);
        }
        Integer current = skuStockMapper.selectAvailable(skuId);
        if (current == null) {
            throw MallErrorCode.exception(MallErrorCode.STOCK_NOT_FOUND);
        }
        throw MallErrorCode.exception(MallErrorCode.STOCK_INVALID,
                "调整后库存不能为负（当前可售 " + current + "，本次调整 " + delta + "）");
    }

    /**
     * 读取可售数量。
     *
     * @param skuId SKU ID
     * @return 可售数量
     */
    private int availableOf(Long skuId) {
        Integer current = skuStockMapper.selectAvailable(skuId);
        if (current == null) {
            throw MallErrorCode.exception(MallErrorCode.STOCK_NOT_FOUND);
        }
        return current;
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private SkuStock toDomain(SkuStockEntity entity) {
        SkuStock stock = new SkuStock();
        stock.setSkuId(entity.getSkuId());
        stock.setAvailableQty(entity.getAvailableQty());
        stock.setReservedQty(entity.getReservedQty());
        stock.setUpdateTime(entity.getUpdateTime());
        return stock;
    }
}
