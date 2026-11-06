package com.bluewhale.lms.interfaces.job;

import com.bluewhale.lms.integration.application.service.UrgeAppService;
import com.bluewhale.lms.integration.application.view.UrgeRunView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 未完成必修催办任务（默认每日 09:00）。
 *
 * <p>用应用内 {@code @Scheduled} 而不是注册到 whale-job：催办是 LMS 自己的领域动作，
 * 需要读培训认定、员工快照与通知留痕三张本地表，走外部调度反而要为一个已经
 * 幂等的任务再维护一层远程回调。任务本身幂等，重复触发不会重复发。</p>
 *
 * <p>异常在此处吞掉并记日志：调度线程抛出异常会静默终止后续执行，
 * 而催办本该第二天继续跑。</p>
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
     */
    @Scheduled(cron = "${whale.lms.urge.cron:0 0 9 * * *}")
    public void urgeDaily() {
        try {
            UrgeRunView result = urgeAppService.runScheduled();
            if (result == null) {
                return;
            }
            if (result.getNotified() > 0 || result.getFailedClasses() > 0) {
                log.info("每日催办完成: day={}, scanned={}, urgent={}, notified={}, failed={}",
                        result.getDay(), result.getScannedClasses(), result.getUrgentClasses(),
                        result.getNotified(), result.getFailedClasses());
            }
        } catch (RuntimeException exception) {
            log.warn("每日催办异常终止: error={}", exception.getMessage());
        }
    }
}
