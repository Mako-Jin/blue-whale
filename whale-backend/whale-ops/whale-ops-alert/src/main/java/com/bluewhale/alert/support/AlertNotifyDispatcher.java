package com.bluewhale.alert.support;

import com.bluewhale.alert.po.AlertRecordPo;
import com.bluewhale.alert.po.AlertRulePo;
import com.bluewhale.alert.service.AlertNotifyService;
import com.bluewhale.common.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * 告警通知异步派发器。
 *
 * <p>通知（邮件/Webhook/短信）均为外部 IO，耗时不可控。落库事务提交后由本派发器
 * 提交到独立线程池执行，避免长时间占用数据库连接，也避免「事务回滚但通知已发出」。
 * 派发时显式传递发起线程的租户ID，因为通知载荷需要读取 {@link TenantContext}。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class AlertNotifyDispatcher {

    private final AlertNotifyService alertNotifyService;

    private final Executor executor;

    /**
     * 构造派发器
     *
     * @param alertNotifyService 通知服务
     * @param executor           通知专用线程池
     */
    public AlertNotifyDispatcher(AlertNotifyService alertNotifyService,
                                 @Qualifier("alertNotifyExecutor") Executor executor) {
        this.alertNotifyService = alertNotifyService;
        this.executor = executor;
    }

    /**
     * 异步派发一条告警通知。
     *
     * @param tenantId 告警归属租户（可为 {@code null}，按平台租户处理）
     * @param rule     命中的规则
     * @param record   告警记录
     */
    public void dispatch(Long tenantId, AlertRulePo rule, AlertRecordPo record) {
        if (rule == null || record == null) {
            return;
        }
        executor.execute(() -> notifyInTenant(tenantId, rule, record));
    }

    /**
     * 在指定租户上下文内发送通知，并恢复线程原有上下文。
     *
     * @param tenantId 告警归属租户
     * @param rule     命中的规则
     * @param record   告警记录
     */
    private void notifyInTenant(Long tenantId, AlertRulePo rule, AlertRecordPo record) {
        Long previous = TenantContext.get();
        TenantContext.set(tenantId == null ? TenantContext.DEFAULT_TENANT_ID : tenantId);
        try {
            alertNotifyService.notify(rule, record);
        } catch (RuntimeException e) {
            log.error("告警通知异步发送异常: ruleCode={}, reason={}",
                    rule.getRuleCode(), e.getMessage(), e);
        } finally {
            TenantContext.set(previous);
        }
    }
}
