package com.bluewhale.pay.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pay.dto.PayReconcileBatchQueryRequest;
import com.bluewhale.pay.dto.PayReconcileDiffQueryRequest;
import com.bluewhale.pay.dto.PayReconcileHandleRequest;
import com.bluewhale.pay.dto.PayReconcileRunRequest;
import com.bluewhale.pay.vo.PayReconcileBatchVO;
import com.bluewhale.pay.vo.PayReconcileDiffVO;

import java.util.List;

/**
 * 批量对账与差异闭环服务。
 *
 * <p>批次以「租户 + 渠道 + 业务日」唯一，重复触发直接返回已有批次；
 * 渠道不支持账单下载时降级为逐笔状态查询并标记 {@code PARTIAL}；
 * 取不到账单则批次 {@code FAILED} 且不产出差异，避免把「取不到账单」误判为「渠道无单」。</p>
 *
 * @author BlueWhale
 */
public interface PayReconcileService {

    /**
     * 手动触发某业务日的对账。
     *
     * @param request 业务日与渠道；业务日缺省 T-1，渠道缺省或 ALL 表示当日全部渠道
     * @return 本次涉及的对账批次
     */
    List<PayReconcileBatchVO> run(PayReconcileRunRequest request);

    /**
     * 日切批量对账（定时入口）。
     *
     * @return 本轮执行的批次数
     */
    int runScheduled();

    /**
     * 对账批次分页。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<PayReconcileBatchVO> pageBatches(PayReconcileBatchQueryRequest request);

    /**
     * 对账差异分页。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<PayReconcileDiffVO> pageDiffs(PayReconcileDiffQueryRequest request);

    /**
     * 处置一条对账差异。
     *
     * <p>{@code COMPENSATE} 仅对「渠道 PAID、本地 PAYING」的状态不一致差异放行，
     * 其余一律拒绝；补偿复用统一落账入口，不另写状态推进。</p>
     *
     * @param id         差异 ID
     * @param request    处置动作与备注
     * @param operatorId 处置人用户 ID（可为空）
     * @return 处置后的差异
     */
    PayReconcileDiffVO handle(Long id, PayReconcileHandleRequest request, Long operatorId);
}
