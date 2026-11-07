package com.bluewhale.erp.mfg.application.event;

/**
 * 生产订单已下达事件（事务提交后触发 MES 下发）。
 *
 * @param orderId 生产订单主键
 * @author BlueWhale
 */
public record ProductionOrderReleasedEvent(Long orderId) {
}
