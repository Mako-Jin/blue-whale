package com.bluewhale.file.schedule;

import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.service.FileInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 存量历史版本定时清理任务。
 *
 * <p>批次 18 的版本保留上限只在新版本产生时生效；本任务按 cron 扫描全部文件，清理存量文件中
 * 超出上限的最旧版本。默认关闭，需要显式开启；无租户上下文，可跨租户清理，且不发布领域事件。</p>
 *
 * <p>由调度中心（whale-job）托管，任务编码 {@code file.version-cleanup}；清理按版本行物理删除，
 * 重复执行不会误删根版本，单个版本失败保留待下轮重试。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VersionCleanupTask {

    private final FileInfoService fileInfoService;

    private final FileProperties fileProperties;

    /**
     * 按配置周期清理存量超限版本；开关关闭或保留上限非正时返回跳过摘要。
     *
     * <p>版本行按物理删除，属不可逆操作，每次执行留一条运行审计：无操作者与请求参数，
     * 执行摘要写入 {@code result_body}，单条版本明细见业务日志。</p>
     *
     * @return 执行摘要（清理条数或跳过原因）
     */
    @ScheduledJob(code = "file.version-cleanup",
            cron = "${whale.file.version.cleanup-cron:0 30 4 * * ?}",
            timeoutSeconds = 3600)
    @AuditLog(operation = "存量历史版本清理", type = OperateType.DELETE,
            targetType = "file_info", recordParams = false, recordResult = true)
    public String cleanup() {
        FileProperties.Version config = fileProperties.getVersion();
        if (!config.isCleanupEnabled()) {
            return "skipped: cleanup disabled";
        }
        if (config.getMaxVersions() <= 0) {
            return "skipped: max-versions<=0";
        }
        int pruned = fileInfoService.pruneExcessVersions();
        if (pruned > 0) {
            log.info("存量历史版本定时清理完成: pruned={}", pruned);
        }
        return "pruned=" + pruned;
    }
}
