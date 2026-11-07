package com.bluewhale.pay.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pay.vo.PayRefundVO;

import java.math.BigDecimal;

/**
 * 退款单服务。
 *
 * <p>退款单模型支持部分退款；累计退款额在 {@code pay_order} 上原子累计，
 * 全额退完自动置 REFUNDED 并投递业务通知。</p>
 *
 * @author BlueWhale
 */
public interface PayRefundService {

    /**
     * 按支付单号退款（内网/管理端共用）。
     *
     * @param payOrderNo 支付单号
     * @param amount     本次退款金额
     * @param reason     退款原因
     * @return 退款单
     */
    PayRefundVO refundByNo(String payOrderNo, BigDecimal amount, String reason);

    /**
     * 管理端整单退款（兼容原 {@code POST /pay/order/{id}/refund} 语义）。
     *
     * @param payOrderId 支付单 ID
     * @param reason     退款原因
     * @return 退款单
     */
    PayRefundVO refundFull(Long payOrderId, String reason);

    /**
     * 退款单分页。
     *
     * @param keyword 退款单号/支付单号模糊（可空）
     * @param current 页码
     * @param size    页大小
     * @return 分页结果
     */
    PageResult<PayRefundVO> page(String keyword, long current, long size);

    /**
     * 退款单详情。
     *
     * @param id 退款单 ID
     * @return 退款单
     */
    PayRefundVO detail(Long id);
}
