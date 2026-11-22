package com.bluewhale.pay.service;

/**
 * 支付超时自动关单服务。
 *
 * @author BlueWhale
 */
public interface PayCloseService {

    /**
     * 扫描并关闭超时未支付单（定时任务入口）。
     *
     * @return 本轮成功关单的笔数
     */
    int closeTimeoutOrders();
}
