package com.bluewhale.oa.hr.interfaces.job;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.oa.hr.application.service.TripReportReminderAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 出差报告未交提醒任务。
 *
 * <p>每天按配置时间扫描已结束超过宽限期且报告未提交的出差单，向申请人发送站内信；
 * 开关与批量可配，单条失败不影响后续记录。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripReportReminderJob {

    private final TripReportReminderAppService tripReportReminderAppService;

    /** 提醒开关 */
    @Value("${whale.oa.trip.report-reminder.enabled:true}")
    private boolean enabled;

    /** 出差结束后第几天开始提醒 */
    @Value("${whale.oa.trip.report-reminder.days-after:3}")
    private long daysAfter;

    /** 单批扫描条数 */
    @Value("${whale.oa.trip.report-reminder.batch-size:100}")
    private int batchSize;

    /**
     * 扫描并提醒逾期未提交报告的出差单。
     *
     * @return 执行摘要（{@code skipped} 或截止日/提醒条数）
     */
    @ScheduledJob(code = "oa.trip.report-reminder",
            cron = "${whale.oa.trip.report-reminder.cron:0 0 9 * * *}",
            timeoutSeconds = 900)
    public String remind() {
        if (!enabled) {
            return "skipped: report-reminder disabled";
        }
        LocalDate deadline = LocalDate.now().minusDays(Math.max(daysAfter, 0L));
        int notified = tripReportReminderAppService
                .remindOverdueReports(deadline, Math.max(batchSize, 1));
        if (notified > 0) {
            log.info("出差报告提醒任务完成: deadline={}, notified={}", deadline, notified);
        }
        return "deadline=" + deadline + ", notified=" + notified;
    }
}
