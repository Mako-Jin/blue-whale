package com.bluewhale.mall.shipment.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.mall.shipment.domain.model.Shipment;
import com.bluewhale.mall.shipment.domain.model.ShipmentItem;
import com.bluewhale.mall.shipment.domain.model.ShipmentQuery;
import com.bluewhale.mall.shipment.domain.model.ShipmentStatus;
import com.bluewhale.mall.shipment.domain.repository.ShipmentRepository;
import com.bluewhale.mall.shipment.infrastructure.persistence.entity.ShipmentEntity;
import com.bluewhale.mall.shipment.infrastructure.persistence.entity.ShipmentItemEntity;
import com.bluewhale.mall.shipment.infrastructure.persistence.mapper.ShipmentItemMapper;
import com.bluewhale.mall.shipment.infrastructure.persistence.mapper.ShipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 发货单仓储实现（MyBatis-Plus）。
 *
 * <p>列表查询<b>不加载明细</b>：发货单列表只需要单号、运单号与件数，
 * 一次带出全部明细会把翻页变成 N+1 查询；明细只在详情与按单号查时加载。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ShipmentRepositoryImpl implements ShipmentRepository {

    private final ShipmentMapper shipmentMapper;

    private final ShipmentItemMapper shipmentItemMapper;

    @Override
    public List<Shipment> page(ShipmentQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<ShipmentEntity> page = shipmentMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ShipmentQuery query) {
        return shipmentMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Shipment> findById(Long id) {
        return Optional.ofNullable(shipmentMapper.selectById(id)).map(entity -> withItems(toDomain(entity)));
    }

    @Override
    public Optional<Shipment> findByNo(String shipmentNo) {
        if (!StringUtils.hasText(shipmentNo)) {
            return Optional.empty();
        }
        ShipmentEntity entity = shipmentMapper.selectOne(new LambdaQueryWrapper<ShipmentEntity>()
                .eq(ShipmentEntity::getShipmentNo, shipmentNo.trim()));
        return Optional.ofNullable(entity).map(found -> withItems(toDomain(found)));
    }

    @Override
    public List<Shipment> listByOrderId(Long orderId) {
        if (orderId == null) {
            return List.of();
        }
        List<ShipmentEntity> entities = shipmentMapper.selectList(new LambdaQueryWrapper<ShipmentEntity>()
                .eq(ShipmentEntity::getOrderId, orderId)
                .orderByAsc(ShipmentEntity::getId));
        return entities.stream().map(entity -> withItems(toDomain(entity))).toList();
    }

    @Override
    public boolean existsTrackingNo(String carrierCode, String trackingNo) {
        if (!StringUtils.hasText(carrierCode) || !StringUtils.hasText(trackingNo)) {
            return false;
        }
        return shipmentMapper.selectCount(new LambdaQueryWrapper<ShipmentEntity>()
                .eq(ShipmentEntity::getCarrierCode, carrierCode.trim().toUpperCase(Locale.ROOT))
                .eq(ShipmentEntity::getTrackingNo, trackingNo.trim())) > 0;
    }

    @Override
    public Long insert(Shipment shipment) {
        ShipmentEntity entity = toEntity(shipment);
        shipmentMapper.insert(entity);
        shipment.setId(entity.getId());
        List<ShipmentItem> items = shipment.getItems();
        if (items != null) {
            for (ShipmentItem item : items) {
                ShipmentItemEntity itemEntity = toItemEntity(item, entity.getId());
                shipmentItemMapper.insert(itemEntity);
                item.setId(itemEntity.getId());
                item.setShipmentId(entity.getId());
            }
        }
        return entity.getId();
    }

    @Override
    public void updateStatus(Shipment shipment) {
        ShipmentEntity entity = new ShipmentEntity();
        entity.setId(shipment.getId());
        entity.setStatus(shipment.getStatus() == null ? null : shipment.getStatus().name());
        entity.setDeliveredTime(shipment.getDeliveredTime());
        entity.setCancelTime(shipment.getCancelTime());
        entity.setCancelReason(shipment.getCancelReason());
        shipmentMapper.updateById(entity);
    }

    /**
     * 组装查询条件。
     *
     * <p>关键字同时匹配发货单号、订单号、运单号：客服手里可能只有一个运单号，
     * 让他先猜「该用哪个筛选框」纯属自找麻烦。</p>
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<ShipmentEntity> filter(ShipmentQuery query) {
        LambdaQueryWrapper<ShipmentEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null && StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(nested -> nested
                    .like(ShipmentEntity::getShipmentNo, keyword)
                    .or().like(ShipmentEntity::getOrderNo, keyword)
                    .or().like(ShipmentEntity::getTrackingNo, keyword));
        }
        if (query != null && StringUtils.hasText(query.getStatus())) {
            wrapper.eq(ShipmentEntity::getStatus, query.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (query != null && StringUtils.hasText(query.getCarrierCode())) {
            wrapper.eq(ShipmentEntity::getCarrierCode,
                    query.getCarrierCode().trim().toUpperCase(Locale.ROOT));
        }
        if (query != null && query.getOrderId() != null) {
            wrapper.eq(ShipmentEntity::getOrderId, query.getOrderId());
        }
        return wrapper.orderByDesc(ShipmentEntity::getId);
    }

    /**
     * 加载明细并挂到发货单上。
     *
     * @param shipment 发货单
     * @return 带明细的发货单
     */
    private Shipment withItems(Shipment shipment) {
        if (shipment == null || shipment.getId() == null) {
            return shipment;
        }
        List<ShipmentItemEntity> entities = shipmentItemMapper.selectList(
                new LambdaQueryWrapper<ShipmentItemEntity>()
                        .eq(ShipmentItemEntity::getShipmentId, shipment.getId())
                        .orderByAsc(ShipmentItemEntity::getId));
        shipment.setItems(entities.stream().map(this::toItemDomain).toList());
        return shipment;
    }

    /**
     * 实体转领域模型（不含明细）。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Shipment toDomain(ShipmentEntity entity) {
        Shipment shipment = new Shipment();
        shipment.setId(entity.getId());
        shipment.setShipmentNo(entity.getShipmentNo());
        shipment.setOrderId(entity.getOrderId());
        shipment.setOrderNo(entity.getOrderNo());
        shipment.setChannelId(entity.getChannelId());
        shipment.setCarrierCode(entity.getCarrierCode());
        shipment.setCarrierName(entity.getCarrierName());
        shipment.setTrackingNo(entity.getTrackingNo());
        shipment.setStatus(ShipmentStatus.parse(entity.getStatus()));
        shipment.setItemCount(entity.getItemCount());
        shipment.setTotalQuantity(entity.getTotalQuantity());
        shipment.setShippedTime(entity.getShippedTime());
        shipment.setDeliveredTime(entity.getDeliveredTime());
        shipment.setCancelTime(entity.getCancelTime());
        shipment.setCancelReason(entity.getCancelReason());
        shipment.setRemark(entity.getRemark());
        shipment.setCreateTime(entity.getCreateTime());
        shipment.setUpdateTime(entity.getUpdateTime());
        return shipment;
    }

    /**
     * 领域模型转实体。
     *
     * @param shipment 领域模型
     * @return 实体
     */
    private ShipmentEntity toEntity(Shipment shipment) {
        ShipmentEntity entity = new ShipmentEntity();
        entity.setId(shipment.getId());
        entity.setShipmentNo(shipment.getShipmentNo());
        entity.setOrderId(shipment.getOrderId());
        entity.setOrderNo(shipment.getOrderNo());
        entity.setChannelId(shipment.getChannelId());
        entity.setCarrierCode(shipment.getCarrierCode());
        entity.setCarrierName(shipment.getCarrierName());
        entity.setTrackingNo(shipment.getTrackingNo());
        entity.setStatus(shipment.getStatus() == null
                ? ShipmentStatus.SHIPPED.name() : shipment.getStatus().name());
        entity.setItemCount(shipment.getItemCount());
        entity.setTotalQuantity(shipment.getTotalQuantity());
        entity.setShippedTime(shipment.getShippedTime());
        entity.setDeliveredTime(shipment.getDeliveredTime());
        entity.setCancelTime(shipment.getCancelTime());
        entity.setCancelReason(shipment.getCancelReason());
        entity.setRemark(shipment.getRemark());
        return entity;
    }

    /**
     * 明细实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private ShipmentItem toItemDomain(ShipmentItemEntity entity) {
        ShipmentItem item = new ShipmentItem();
        item.setId(entity.getId());
        item.setShipmentId(entity.getShipmentId());
        item.setOrderId(entity.getOrderId());
        item.setOrderItemId(entity.getOrderItemId());
        item.setSpuId(entity.getSpuId());
        item.setSkuId(entity.getSkuId());
        item.setSpuName(entity.getSpuName());
        item.setSkuCode(entity.getSkuCode());
        item.setSpecJson(entity.getSpecJson());
        item.setQuantity(entity.getQuantity());
        return item;
    }

    /**
     * 明细领域模型转实体。
     *
     * @param item 领域模型
     * @param shipmentId 发货单 ID
     * @return 实体
     */
    private ShipmentItemEntity toItemEntity(ShipmentItem item, Long shipmentId) {
        ShipmentItemEntity entity = new ShipmentItemEntity();
        entity.setShipmentId(shipmentId);
        entity.setOrderId(item.getOrderId());
        entity.setOrderItemId(item.getOrderItemId());
        entity.setSpuId(item.getSpuId());
        entity.setSkuId(item.getSkuId());
        entity.setSpuName(item.getSpuName());
        entity.setSkuCode(item.getSkuCode());
        entity.setSpecJson(item.getSpecJson());
        entity.setQuantity(item.getQuantity());
        return entity;
    }
}
