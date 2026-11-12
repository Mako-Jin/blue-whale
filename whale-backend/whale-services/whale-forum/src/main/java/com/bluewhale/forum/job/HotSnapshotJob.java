package com.bluewhale.forum.job;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.service.HotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 热榜快照刷新任务。
 *
 * <p>走平台受管任务（{@code @ScheduledJob}）而不是裸 {@code @Scheduled}：任务编码会登记进
 * 执行器注册表，运维可随时改由调度中心（whale-job）触发、或在平台上手动补跑，
 * 不必改代码重启。默认仍在进程内按 cron 跑。</p>
 *
 * <p>整表覆盖天然幂等，多实例同时跑不会写出两份数据，最多浪费一次聚合。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.forum", name = "hot-refresh-enabled",
        havingValue = "true", matchIfMissing = true)
public class HotSnapshotJob {

    private final HotService hotService;

    private final ForumProperties properties;

    /**
     * 刷新所有租户的热榜快照。
     *
     * @return 执行摘要（条数）
     */
    @ScheduledJob(code = "forum.hot.snapshot",
            cron = "${whale.forum.hot-refresh-cron:0 0/30 * * * ?}", timeoutSeconds = 300)
    public String refresh() {
        if (!properties.isHotRefreshEnabled()) {
            return "SKIPPED: whale.forum.hot-refresh-enabled=false";
        }
        int count = hotService.refreshAllTenants();
        return "OK: hot snapshot rows=" + count;
    }
}
