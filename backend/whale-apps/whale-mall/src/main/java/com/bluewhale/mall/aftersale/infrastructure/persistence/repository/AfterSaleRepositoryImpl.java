package com.bluewhale.mall.aftersale.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.mall.aftersale.domain.model.AfterSaleItem;
import com.bluewhale.mall.aftersale.domain.model.AfterSaleStatus;
import com.bluewhale.mall.aftersale.domain.model.AfterSaleType;
import com.bluewhale.mall.aftersale.domain.model.MallAfterSale;
import com.bluewhale.mall.aftersale.domain.repository.AfterSaleRepository;
import com.bluewhale.mall.aftersale.infrastructure.persistence.entity.AfterSaleEntity;
import com.bluewhale.mall.aftersale.infrastructure.persistence.entity.AfterSaleItemEntity;
import com.bluewhale.mall.aftersale.infrastructure.persistence.mapper.AfterSaleItemMapper;
import com.bluewhale.mall.aftersale.infrastructure.persistence.mapper.AfterSaleMapper;
import com.bluewhale.mall.order.infrastructure.persistence.mapper.OrderItemMapper;
import com.bluewhale.mall.order.infrastructure.persistence.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 售后单仓储实现（MyBatis-Plus）。
 *
 * <p>列表查询一次性批量捞明细，避免每行售后单再查一次明细的 N+1。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AfterSaleRepositoryImpl implements AfterSaleRepository {

    private final AfterSaleMapper afterSaleMapper;

    private final AfterSaleItemMapper afterSaleItemMapper;

    private final OrderItemMapper orderItemMapper;

    private final OrderMapper orderMapper;

    @Override
    public Long insert(MallAfterSale afterSale) {
        AfterSaleEntity entity = toEntity(afterSale);
        afterSaleMapper.insert(entity);
        afterSale.setId(entity.getId());
        for (AfterSaleItem item : afterSale.getItems()) {
            AfterSaleItemEntity itemEntity = toItemEntity(item, entity.getId());
            afterSaleItemMapper.insert(itemEntity);
            item.setId(itemEntity.getId());
            item.setAfterSaleId(entity.getId());
        }
        return entity.getId();
    }

    @Override
    public void update(MallAfterSale afterSale) {
        afterSaleMapper.updateById(toEntity(afterSale));
    }

    @Override
    public Optional<MallAfterSale> findById(Long id) {
        AfterSaleEntity entity = afterSaleMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        MallAfterSale afterSale = toDomain(entity);
        afterSale.setItems(loadItems(List.of(entity.getId())).getOrDefault(entity.getId(), new ArrayList<>()));
        return Optional.of(afterSale);
    }

    @Override
    public Optional<MallAfterSale> findByAfterSaleNo(String afterSaleNo) {
        if (!StringUtils.hasText(afterSaleNo)) {
            return Optional.empty();
        }
        AfterSaleEntity entity = afterSaleMapper.selectOne(new LambdaQueryWrapper<AfterSaleEntity>()
                .eq(AfterSaleEntity::getAfterSaleNo, afterSaleNo.trim()));
        if (entity == null) {
            return Optional.empty();
        }
        MallAfterSale afterSale = toDomain(entity);
        afterSale.setItems(loadItems(List.of(entity.getId())).getOrDefault(entity.getId(), new ArrayList<>()));
        return Optional.of(afterSale);
    }

    @Override
    public List<MallAfterSale> page(Long userId, String status, String keyword, long offset, int size) {
        int safeSize = Math.max(size, 1);
        long current = Math.max(offset / safeSize + 1, 1L);
        Page<AfterSaleEntity> page = afterSaleMapper.selectPage(new Page<>(current, safeSize),
                filter(userId, status, keyword));
        List<AfterSaleEntity> records = page.getRecords();
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> ids = records.stream().map(AfterSaleEntity::getId).toList();
        Map<Long, List<AfterSaleItem>> items = loadItems(ids);
        List<MallAfterSale> result = new ArrayList<>(records.size());
        for (AfterSaleEntity entity : records) {
            MallAfterSale afterSale = toDomain(entity);
            afterSale.setItems(items.getOrDefault(entity.getId(), new ArrayList<>()));
            result.add(afterSale);
        }
        return result;
    }

    @Override
    public long count(Long userId, String status, String keyword) {
        return afterSaleMapper.selectCount(filter(userId, status, keyword));
    }

    @Override
    public boolean occupyItemQuota(Long orderItemId, int quantity, BigDecimal amount) {
        return orderItemMapper.occupyRefundQuota(orderItemId, quantity, amount) > 0;
    }

    @Override
    public boolean releaseItemQuota(Long orderItemId, int quantity, BigDecimal amount) {
        return orderItemMapper.releaseRefundQuota(orderItemId, quantity, amount) > 0;
    }

    @Override
    public boolean applyOrderRefund(Long orderId, BigDecimal amount) {
        return orderMapper.applyRefund(orderId, amount) > 0;
    }

    /**
     * 批量加载明细并按售后单分组。
     *
     * @param afterSaleIds 售后单 ID 列表
     * @return 售后单 ID → 明细列表
     */
    private Map<Long, List<AfterSaleItem>> loadItems(List<Long> afterSaleIds) {
        Map<Long, List<AfterSaleItem>> result = new HashMap<>();
        if (afterSaleIds == null || afterSaleIds.isEmpty()) {
            return result;
        }
        List<AfterSaleItemEntity> items = afterSaleItemMapper.selectList(
                new LambdaQueryWrapper<AfterSaleItemEntity>()
                        .in(AfterSaleItemEntity::getAfterSaleId, new LinkedHashSet<>(afterSaleIds))
                        .orderByAsc(AfterSaleItemEntity::getId));
        for (AfterSaleItemEntity item : items) {
            result.computeIfAbsent(item.getAfterSaleId(), key -> new ArrayList<>()).add(toDomain(item));
        }
        return result;
    }

    /**
     * 组装查询条件。
     *
     * @param userId  买家用户 ID（null 不限）
     * @param status  状态
     * @param keyword 关键字
     * @return 查询包装器
     */
    private LambdaQueryWrapper<AfterSaleEntity> filter(Long userId, String status, String keyword) {
        LambdaQueryWrapper<AfterSaleEntity> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(AfterSaleEntity::getUserId, userId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(AfterSaleEntity::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim().toUpperCase(Locale.ROOT);
            wrapper.and(inner -> inner.like(AfterSaleEntity::getAfterSaleNo, trimmed)
                    .or().like(AfterSaleEntity::getOrderNo, trimmed));
        }
        return wrapper.orderByDesc(AfterSaleEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private MallAfterSale toDomain(AfterSaleEntity entity) {
        MallAfterSale afterSale = new MallAfterSale();
        afterSale.setId(entity.getId());
        afterSale.setAfterSaleNo(entity.getAfterSaleNo());
        afterSale.setOrderId(entity.getOrderId());
        afterSale.setOrderNo(entity.getOrderNo());
        afterSale.setUserId(entity.getUserId());
        afterSale.setType(AfterSaleType.parse(entity.getType()));
        afterSale.setStatus(AfterSaleStatus.parse(entity.getStatus()));
        afterSale.setRefundAmount(entity.getRefundAmount());
        afterSale.setReason(entity.getReason());
        afterSale.setDescription(entity.getDescription());
        afterSale.setImages(entity.getImages());
        afterSale.setAuditUserId(entity.getAuditUserId());
        afterSale.setAuditUserName(entity.getAuditUserName());
        afterSale.setAuditTime(entity.getAuditTime());
        afterSale.setAuditRemark(entity.getAuditRemark());
        afterSale.setRefundNo(entity.getRefundNo());
        afterSale.setRefundTime(entity.getRefundTime());
        afterSale.setReturnLogisticsNo(entity.getReturnLogisticsNo());
        afterSale.setCreateTime(entity.getCreateTime());
        return afterSale;
    }

    /**
     * 明细实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private AfterSaleItem toDomain(AfterSaleItemEntity entity) {
        AfterSaleItem item = new AfterSaleItem();
        item.setId(entity.getId());
        item.setAfterSaleId(entity.getAfterSaleId());
        item.setOrderItemId(entity.getOrderItemId());
        item.setSpuId(entity.getSpuId());
        item.setSkuId(entity.getSkuId());
        item.setSpuName(entity.getSpuName());
        item.setSkuCode(entity.getSkuCode());
        item.setUnitPrice(entity.getUnitPrice());
        item.setQuantity(entity.getQuantity());
        item.setAmount(entity.getAmount());
        return item;
    }

    /**
     * 领域模型转实体。
     *
     * @param afterSale 售后单
     * @return 实体
     */
    private AfterSaleEntity toEntity(MallAfterSale afterSale) {
        AfterSaleEntity entity = new AfterSaleEntity();
        entity.setId(afterSale.getId());
        entity.setAfterSaleNo(afterSale.getAfterSaleNo());
        entity.setOrderId(afterSale.getOrderId());
        entity.setOrderNo(afterSale.getOrderNo());
        entity.setUserId(afterSale.getUserId());
        entity.setType(afterSale.getType() == null ? null : afterSale.getType().name());
        entity.setStatus(afterSale.getStatus() == null ? null : afterSale.getStatus().name());
        entity.setRefundAmount(afterSale.getRefundAmount());
        entity.setReason(afterSale.getReason());
        entity.setDescription(afterSale.getDescription());
        entity.setImages(afterSale.getImages());
        entity.setAuditUserId(afterSale.getAuditUserId());
        entity.setAuditUserName(afterSale.getAuditUserName());
        entity.setAuditTime(afterSale.getAuditTime());
        entity.setAuditRemark(afterSale.getAuditRemark());
        entity.setRefundNo(afterSale.getRefundNo());
        entity.setRefundTime(afterSale.getRefundTime());
        entity.setReturnLogisticsNo(afterSale.getReturnLogisticsNo());
        return entity;
    }

    /**
     * 领域模型转明细实体。
     *
     * @param item        明细
     * @param afterSaleId 售后单 ID
     * @return 实体
     */
    private AfterSaleItemEntity toItemEntity(AfterSaleItem item, Long afterSaleId) {
        AfterSaleItemEntity entity = new AfterSaleItemEntity();
        entity.setId(item.getId());
        entity.setAfterSaleId(afterSaleId);
        entity.setOrderItemId(item.getOrderItemId());
        entity.setSpuId(item.getSpuId());
        entity.setSkuId(item.getSkuId());
        entity.setSpuName(item.getSpuName());
        entity.setSkuCode(item.getSkuCode());
        entity.setUnitPrice(item.getUnitPrice());
        entity.setQuantity(item.getQuantity());
        entity.setAmount(item.getAmount());
        return entity;
    }
}
