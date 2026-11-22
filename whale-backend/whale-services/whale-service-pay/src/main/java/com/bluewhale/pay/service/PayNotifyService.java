package com.bluewhale.pay.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pay.po.PayNotifyTaskPo;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.enums.PayNotifyEvent;
import com.bluewhale.pay.vo.PayNotifyRetryBatchItemVO;
import com.bluewhale.pay.vo.PayNotifyRetryResultVO;

import java.util.List;

/**
 * 业务通知服务。
 *
 * <p>支付/退款成功后投递 {@link PayNotifyTaskPo}：先落任务表，事务提交后异步首投；
 * 失败由重试器按指数退避重试，超过上限置 DEAD 等待人工重试。</p>
 *
 * @author BlueWhale
 */
public interface PayNotifyService {

    /**
     * 落一条通知任务（随当前事务提交）。
     *
     * @param event      事件
     * @param order      支付单（须已带 notifyUrl）
     * @param refundNo   退款单号（REFUNDED 事件用，可为 null）
     * @param refundAmount 累计退款金额（REFUNDED 事件用，可为 null）
     * @return 任务实体
     */
    PayNotifyTaskPo enqueue(PayNotifyEvent event, PayOrderPo order, String refundNo, java.math.BigDecimal refundAmount);

    /**
     * 事务提交后异步首投；无事务上下文时直接异步投递。
     *
     * @param taskId 任务 ID
     */
    void dispatchAfterCommit(Long taskId);

    /**
     * 同步投递一次（供重试器与测试调用）：抢占 → HTTP 回调 → 按结果更新状态。
     *
     * @param taskId 任务 ID
     * @return 是否投递成功
     */
    boolean deliverTask(Long taskId);

    /**
     * 重试到期的 PENDING 任务（定时器入口）。
     *
     * @return 本轮投递的任务数
     */
    int retryDueTasks();

    /**
     * 手工重试 DEAD/PENDING 任务（管理端入口）。
     *
     * <p>返回三态结果：投递成功、正在投递（被其他实例领取）、已排下次重试；
     * 任务不存在时抛出 {@code NOT_FOUND}。</p>
     *
     * @param taskId 任务 ID
     * @return 三态结果
     */
    PayNotifyRetryResultVO retryManually(Long taskId);

    /**
     * 批量手工重试（管理端入口）。
     *
     * <p>按入参顺序去重后逐项投递，单项失败不影响其余项；逐项返回结果，
     * 不存在的任务返回 {@code NOT_FOUND}。</p>
     *
     * @param taskIds 任务 ID 列表
     * @return 逐项结果（与去重后的入参顺序一致）
     */
    List<PayNotifyRetryBatchItemVO> retryBatch(List<Long> taskIds);

    /**
     * 查询任务列表（管理端分页）。
     *
     * @param current 页码
     * @param size    页大小
     * @param status  状态过滤（可空）
     * @param bizType 业务方类型过滤（可空）
     * @return 分页结果
     */
    PageResult<PayNotifyTaskPo> pageTasks(long current, long size, String status, String bizType);

    /**
     * 任务详情（含载荷，供死信排查）。
     *
     * @param taskId 任务 ID
     * @return 任务实体
     */
    PayNotifyTaskPo getTask(Long taskId);
}
