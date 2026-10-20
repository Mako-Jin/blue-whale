package com.bluewhale.pay.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pay.dto.PayOrderCreateRequest;
import com.bluewhale.pay.dto.PayOrderQueryRequest;
import com.bluewhale.pay.vo.PayOrderVO;

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
     * 支付：仅允许 CREATED → PAID，流转后回填支付时间
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
}
