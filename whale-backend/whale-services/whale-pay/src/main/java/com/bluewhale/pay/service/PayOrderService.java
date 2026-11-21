package com.bluewhale.pay.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pay.dto.PayInternalCreateRequest;
import com.bluewhale.pay.dto.PayOrderCreateRequest;
import com.bluewhale.pay.dto.PayOrderQueryRequest;
import com.bluewhale.pay.vo.PayOrderVO;
import com.bluewhale.pay.vo.PayReconcileVO;
import com.bluewhale.pay.vo.PayTransactionVO;

import java.util.List;

/**
 * 支付单服务
 *
 * @author BlueWhale
 */
public interface PayOrderService {

    /**
     * 分页查询支付单
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<PayOrderVO> page(PayOrderQueryRequest request);

    /**
     * 支付单详情
     *
     * @param id 支付单ID
     * @return 支付单视图对象
     */
    PayOrderVO getDetail(Long id);

    /**
     * 创建支付单（初始状态 CREATED）
     *
     * @param request 创建请求
     * @return 创建后的支付单视图对象
     */
    PayOrderVO create(PayOrderCreateRequest request);

    /**
     * 内网创建支付单（业务服务调用；按 orderNo 幂等：已存在直接返回原单）
     *
     * @param request 内网创建请求
     * @return 支付单视图对象
     */
    PayOrderVO createInternal(PayInternalCreateRequest request);

    /**
     * 按支付单号查询
     *
     * @param orderNo 支付单号
     * @return 支付单视图对象
     */
    PayOrderVO getByOrderNo(String orderNo);

    /**
     * 按支付单号发起支付（成功后投递 PAID 业务通知）
     *
     * @param orderNo 支付单号
     * @return 支付后的支付单视图对象
     */
    PayOrderVO payByOrderNo(String orderNo);

    /**
     * 按支付单号取消（已取消幂等成功）
     *
     * @param orderNo 支付单号
     * @return 取消后的支付单视图对象
     */
    PayOrderVO cancelByOrderNo(String orderNo);

    /**
     * 支付：仅允许 CREATED → PAYING → PAID；PAYING 为渠道调用中间态，
     * 渠道明确失败回退 CREATED，成功后回填支付时间与渠道流水号
     *
     * @param id 支付单ID
     * @return 支付后的支付单视图对象
     */
    PayOrderVO pay(Long id);

    /**
     * 取消：仅允许 CREATED → CANCELLED
     *
     * @param id 支付单ID
     * @return 取消后的支付单视图对象
     */
    PayOrderVO cancel(Long id);

    /**
     * 退款：仅允许 PAID 态；走退款单模型（当前管理端为整单退款），
     * 成功后投递 REFUNDED 业务通知
     *
     * @param id 支付单ID
     * @return 退款后的支付单视图对象
     */
    PayOrderVO refund(Long id);

    /**
     * 对账：比对本地状态与渠道状态
     *
     * @param id 支付单ID
     * @return 对账结果
     */
    PayReconcileVO reconcile(Long id);

    /**
     * 支付单的支付/退款流水（按创建时间倒序）
     *
     * @param id 支付单ID
     * @return 流水列表
     */
    List<PayTransactionVO> listTransactions(Long id);
}
