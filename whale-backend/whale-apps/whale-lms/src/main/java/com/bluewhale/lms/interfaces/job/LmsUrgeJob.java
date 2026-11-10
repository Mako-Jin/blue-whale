package com.bluewhale.lms.interfaces.job;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.lms.integration.application.service.UrgeAppService;
import com.bluewhale.lms.integration.application.view.UrgeRunView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 未完成必修催办任务（默认每日 09:00）。
 *
 * <p>由 whale-job 调度中心触发，本类只做入口：幂等判定与通知留痕都在
 * {@link UrgeAppService} 内完成，重复触发不会重复发。返回值是执行摘要，
 * 直接落 {@code job_log.result}。</p>
 *
 * <p>整轮异常不再吞掉：原先 {@code catch} 后只记 warn，催办停摆会被调度侧
 * 长期记成成功。抛出后由调度中心记失败并按重试策略处理，次日 cron 仍照常。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LmsUrgeJob {

    private final UrgeAppService urgeAppService;

    /**
     * 每日催办。
     *
     * @return 执行摘要（{@code day/scanned/urgent/notified/failed}）
     */
    @ScheduledJob(code = "lms.urge.daily",
            cron = "${whale.lms.urge.cron:0 0 9 * * *}",
            timeoutSeconds = 1800)
    public String urgeDaily() {
        UrgeRunView result = urgeAppService.runScheduled();
        if (result == null) {
            return "skipped: no urge result";
        }
        log.info("每日催办完成: day={}, scanned={}, urgent={}, notified={}, failed={}",
                result.getDay(), result.getScannedClasses(), result.getUrgentClasses(),
                result.getNotified(), result.getFailedClasses());
        return "day=" + result.getDay()
                + ", scanned=" + result.getScannedClasses()
                + ", urgent=" + result.getUrgentClasses()
                + ", notified=" + result.getNotified()
                + ", failed=" + result.getFailedClasses();
    }
}
