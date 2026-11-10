package com.bluewhale.mall.shipment.domain.repository;

import com.bluewhale.mall.shipment.domain.model.Shipment;
import com.bluewhale.mall.shipment.domain.model.ShipmentQuery;

import java.util.List;
import java.util.Optional;

/**
 * 发货单仓储。
 *
 * @author BlueWhale
 */
public interface ShipmentRepository {

    /**
     * 分页查询。
     *
     * @param query 查询条件
     * @param offset 偏移
     * @param size 每页条数
     * @return 发货单列表（含明细）
     */
    List<Shipment> page(ShipmentQuery query, long offset, int size);

    /**
     * 计数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(ShipmentQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 发货单（含明细）
     */
    Optional<Shipment> findById(Long id);

    /**
     * 按发货单号查询。
     *
     * @param shipmentNo 发货单号
     * @return 发货单（含明细）
     */
    Optional<Shipment> findByNo(String shipmentNo);

    /**
     * 查询某订单的全部发货单（含已作废，按创建时间正序）。
     *
     * @param orderId 订单 ID
     * @return 发货单列表
     */
    List<Shipment> listByOrderId(Long orderId);

    /**
     * 运单号是否已被占用。
     *
     * @param carrierCode 承运商编码
     * @param trackingNo 运单号
     * @return 已占用返回 true
     */
    boolean existsTrackingNo(String carrierCode, String trackingNo);

    /**
     * 新增发货单（含明细）。
     *
     * @param shipment 发货单
     * @return 主键
     */
    Long insert(Shipment shipment);

    /**
     * 更新状态相关字段（签收 / 作废）。
     *
     * @param shipment 发货单
     */
    void updateStatus(Shipment shipment);
}
