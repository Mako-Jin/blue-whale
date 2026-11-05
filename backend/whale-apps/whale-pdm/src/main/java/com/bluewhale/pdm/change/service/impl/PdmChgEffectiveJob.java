package com.bluewhale.pdm.change.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pdm.change.client.PdmChgMessageClient;
import com.bluewhale.pdm.change.entity.PdmChgNotice;
import com.bluewhale.pdm.change.mapper.PdmChgJobLockMapper;
import com.bluewhale.pdm.change.service.PdmChgNoticeService;
import com.bluewhale.pdm.change.support.PdmChgBizType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 变更到期生效任务
 *
 * <p>每日扫描已通知且到达生效日的 ECN，逐条生效；通过 {@code pdm_chg_job_lock}
 * 保证单实例执行，已生效记录会被服务层跳过，重复执行不重复生效。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "pdm.change", name = "effective-job-enabled", havingValue = "true",
        matchIfMissing = true)
public class PdmChgEffectiveJob {

    /** 锁键 */
    private static final String LOCK_KEY = "pdm-change-effective";

    /** 锁持有分钟数 */
    private static final long LOCK_MINUTES = 30L;

    /** 告警事件类型 */
    private static final String EVENT_ALERT = "ECN_ALERT";

    private final PdmChgNoticeService noticeService;

    private final PdmChgJobLockMapper jobLockMapper;

    private final PdmChgMessageClient messageClient;

    /**
     * 每日扫描到期变更通知。
     */
    @Scheduled(cron = "${pdm.change.effective-cron:0 0 2 * * ?}")
    public void scheduledRun() {
        execute();
    }

    /**
     * 执行一次到期生效扫描。
     */
    public void execute() {
        String owner = UUID.randomUUID().toString();
        LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(LOCK_MINUTES);
        jobLockMapper.insertIgnore(LOCK_KEY, owner, lockedUntil);
        if (jobLockMapper.acquire(LOCK_KEY, owner, lockedUntil) <= 0) {
            log.info("变更到期任务未获取到锁，跳过本次执行: owner={}", owner);
            return;
        }
        try {
            List<PdmChgNotice> due = noticeService.dueNotices();
            log.info("变更到期任务开始: dueCount={}", due.size());
            for (PdmChgNotice notice : due) {
                try {
                    noticeService.effective(notice.getId(), null);
                } catch (Exception ex) {
                    log.error("变更通知生效失败: noticeNo={}, id={}", notice.getNoticeNo(), notice.getId(), ex);
                    alert(notice, ex.getMessage());
                }
            }
        } finally {
            jobLockMapper.release(LOCK_KEY, owner);
        }
    }

    /**
     * 发送失败告警，异常只记日志。
     *
     * @param notice  变更通知
     * @param message 失败原因
     */
    private void alert(PdmChgNotice notice, String message) {
        try {
            Long tenantId = TenantContext.get() == null ? 0L : TenantContext.get();
            messageClient.send(tenantId, PdmChgBizType.ECN, String.valueOf(notice.getId()),
                    "ECN-ALERT:" + notice.getId() + ":" + notice.getEffectiveFrom(),
                    "变更通知生效失败：" + notice.getNoticeNo(),
                    message == null ? "未知错误" : message, List.of("INBOX"), List.of());
        } catch (Exception ex) {
            log.warn("变更生效失败告警发送失败: noticeNo={}", notice.getNoticeNo(), ex);
        }
    }
}
