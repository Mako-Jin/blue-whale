package com.bluewhale.file.schedule;

import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.share.repository.FileShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 到期分享链接定时清理任务。
 *
 * <p>分享链接有有效期与下载次数上限，失效后记录仍留在 {@code file_share}，长期累积会拖慢分页
 * 查询。本任务按 cron 物理删除到期超过保留期的分享；撤销但未到期的分享保留至自然到期后清理，
 * 以免丢失撤销痕迹。</p>
 *
 * <p>由调度中心（whale-job）托管，任务编码 {@code file.share-cleanup}；按批删除（单批上限由
 * {@code whale.file.share.purge-batch-size} 控制），单条失败整轮上抛，由调度中心记录失败并重试。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareCleanupTask {

    private final FileShareRepository fileShareRepository;

    private final FileProperties fileProperties;

    /**
     * 按配置周期清理到期分享；开关关闭时返回跳过摘要。
     *
     * <p>每次执行留一条运行审计：物理删除不可逆，需保留「何时清理了多少条」；无操作者与请求
     * 参数，执行摘要写入 {@code result_body}。</p>
     *
     * @return 执行摘要（清理条数或跳过原因）
     */
    @ScheduledJob(code = "file.share-cleanup",
            cron = "${whale.file.share.purge-cron:0 10 4 * * ?}",
            timeoutSeconds = 1800)
    @AuditLog(operation = "到期分享链接清理", type = OperateType.DELETE,
            targetType = "file_share", recordParams = false, recordResult = true)
    public String purgeExpired() {
        FileProperties.Share config = fileProperties.getShare();
        if (!config.isPurgeEnabled()) {
            return "skipped: purge disabled";
        }
        long retentionDays = Math.max(config.getRetentionDays(), 0L);
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        int batchSize = config.getPurgeBatchSize();
        int total = 0;
        int deleted;
        do {
            deleted = fileShareRepository.purgeExpiredBefore(threshold, batchSize);
            total += deleted;
        } while (deleted >= batchSize);
        if (total > 0) {
            log.info("到期分享链接清理完成: purged={}", total);
        }
        return "purged=" + total;
    }
}
