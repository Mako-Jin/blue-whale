package com.bluewhale.job.scheduler.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.job.config.JobProperties;
import com.bluewhale.job.entity.JobLog;
import com.bluewhale.job.mapper.JobLogMapper;
import com.bluewhale.job.scheduler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定时任务日志清理执行器（Bean 名 {@code jobLogCleanupHandler}）。
 *
 * <p>逻辑删除早于 {@code whale.job.log-retention-days} 保留期的 {@code job_log} 记录，
 * 避免执行流水无限增长。采用逻辑删除（{@code deleted=1}）而非物理删除，保留可审计痕迹；
 * 导出归档至对象存储（依赖 whale-file）为后续阶段事项。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component("jobLogCleanupHandler")
@RequiredArgsConstructor
public class JobLogCleanupHandler implements JobHandler {

    private final JobLogMapper jobLogMapper;

    private final JobProperties jobProperties;

    @Override
    public String execute(String param) {
        int retentionDays = jobProperties.getLogRetentionDays();
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        int removed = jobLogMapper.delete(new LambdaQueryWrapper<JobLog>()
                .lt(JobLog::getStartTime, threshold));
        String message = "job_log 清理完成：保留 " + retentionDays + " 天，本次清理 " + removed + " 条";
        log.info(message);
        return message;
    }
}
