package com.bluewhale.pay.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pay.entity.PayNotifyTask;
import com.bluewhale.pay.entity.PayOrder;
import com.bluewhale.pay.enums.PayNotifyEvent;

/**
 * 业务通知服务。
 *
 * <p>支付/退款成功后投递 {@link PayNotifyTask}：先落任务表，事务提交后异步首投；
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
    PayNotifyTask enqueue(PayNotifyEvent event, PayOrder order, String refundNo, java.math.BigDecimal refundAmount);

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
     * @param taskId 任务 ID
     * @return 是否投递成功
     */
    boolean retryManually(Long taskId);

    /**
     * 查询任务列表（管理端分页）。
     *
     * @param current 页码
     * @param size    页大小
     * @param status  状态过滤（可空）
     * @return 分页结果
     */
    PageResult<PayNotifyTask> pageTasks(long current, long size, String status);
}
