package com.bluewhale.pay.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pay.vo.PayRefundVO;

import java.math.BigDecimal;

/**
 * 退款单服务。
 *
 * <p>退款单模型支持部分退款；累计退款额在 {@code pay_order} 上原子累计，
 * 全额退完自动置 REFUNDED 并投递业务通知。渠道退款在数据库事务之外执行，
 * 失败会把退款单置为 FAILED，作为人工对账入口。</p>
 *
 * @author BlueWhale
 */
public interface PayRefundService {

    /**
     * 按支付单号退款（内网/管理端共用）。
     *
     * <p>{@code refundNo} 为业务方生成的退款请求号，作为幂等键：
     * 同号重复调用直接返回原退款单，不重复出账。为空则由支付侧自动生成，
     * 适用于「一次性、不重试」的调用场景。</p>
     *
     * @param payOrderNo 支付单号
     * @param refundNo   业务退款请求号（幂等键，可空）
     * @param amount     本次退款金额
     * @param reason     退款原因
     * @return 退款单
     */
    PayRefundVO refundByNo(String payOrderNo, String refundNo, BigDecimal amount, String reason);

    /**
     * 按支付单号退款（不带幂等键，退款单号由支付侧生成）。
     *
     * @param payOrderNo 支付单号
     * @param amount     本次退款金额
     * @param reason     退款原因
     * @return 退款单
     */
    default PayRefundVO refundByNo(String payOrderNo, BigDecimal amount, String reason) {
        return refundByNo(payOrderNo, null, amount, reason);
    }

    /**
     * 管理端整单退款（兼容原 {@code POST /pay/v1/order/{id}/refund} 语义）。
     *
     * @param payOrderId 支付单 ID
     * @param reason     退款原因
     * @return 退款单
     */
    PayRefundVO refundFull(Long payOrderId, String reason);

    /**
     * 管理端部分退款（按支付单 ID）。
     *
     * <p>金额必须小于等于剩余可退金额；小于剩余金额时要求渠道具备
     * {@code PARTIAL_REFUND} 能力，否则直接拒绝，不允许把部分退款伪装成全额。</p>
     *
     * @param payOrderId     支付单 ID
     * @param clientRequestId 业务退款请求号（幂等键，可空）
     * @param amount         本次退款金额
     * @param reason         退款原因
     * @return 退款单
     */
    PayRefundVO refundPartial(Long payOrderId, String clientRequestId, BigDecimal amount, String reason);

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
