package com.bluewhale.oa.expense.interfaces.job;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.oa.expense.application.service.ExpenseAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 报销单 ERP 应付推送任务。
 *
 * <p>按配置周期扫描已审批通过且推送状态为待推送/失败的报销单，调用 ERP 内网接口创建应付单；
 * 单条失败记录 FAILED 后继续处理后续记录，可由手工重试接口再次触发。由 whale-job
 * 调度中心触发，返回值是执行摘要，直接落 {@code job_log.result}。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpensePayablePushJob {

    private final ExpenseAppService expenseAppService;

    /** 推送开关 */
    @Value("${whale.oa.expense.push.enabled:true}")
    private boolean enabled;

    /**
     * 扫描并推送待推送的报销单。
     *
     * @return 执行摘要（{@code skipped} 或推送成功条数）
     */
    @ScheduledJob(code = "oa.expense.payable-push",
            cron = "${whale.oa.expense.push.cron:0 */10 * * * *}",
            timeoutSeconds = 1800)
    public String push() {
        if (!enabled) {
            return "skipped: expense push disabled";
        }
        int success = expenseAppService.pushPending();
        if (success > 0) {
            log.info("报销单 ERP 应付推送任务完成: success={}", success);
        }
        return "pushed=" + success;
    }
}
