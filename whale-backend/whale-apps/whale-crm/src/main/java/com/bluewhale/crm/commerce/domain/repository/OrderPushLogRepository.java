package com.bluewhale.crm.commerce.domain.repository;

/**
 * 商务订单下发日志仓储端口。
 *
 * @author BlueWhale
 */
public interface OrderPushLogRepository {

    /**
     * 记录一次下发结果。
     *
     * @param orderId 订单 ID
     * @param attemptNo 尝试序号
     * @param requestRef 请求引用
     * @param responseRef 响应引用
     * @param status 结果状态
     * @param errorMsg 失败原因
     * @param costMs 耗时（毫秒）
     */
    void record(Long orderId, Integer attemptNo, String requestRef, String responseRef,
                String status, String errorMsg, long costMs);
}
