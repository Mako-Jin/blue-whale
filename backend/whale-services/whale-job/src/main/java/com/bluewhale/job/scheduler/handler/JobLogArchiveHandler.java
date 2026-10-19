package com.bluewhale.job.scheduler.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.job.config.JobProperties;
import com.bluewhale.job.entity.JobLog;
import com.bluewhale.job.mapper.JobLogMapper;
import com.bluewhale.job.scheduler.JobHandler;
import com.bluewhale.job.scheduler.archive.FileUploadVO;
import com.bluewhale.job.scheduler.archive.JobArchiveClient;
import com.bluewhale.job.scheduler.archive.JobLogCsvWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 定时任务执行日志归档执行器（Bean 名 {@code jobLogArchiveHandler}）。
 *
 * <p>将早于 {@code whale.job.log-retention-days} 保留期、按
 * {@code whale.job.archive-batch-size} 限量的 {@code job_log} 导出为 CSV（UTF-8 BOM）
 * 并上传至 whale-file，<b>仅当上传成功后</b>才逻辑删除该批记录；上传失败
 * （含 whale-file 不可达）不清理任何数据并返回失败摘要，异常绝不外抛打断调度线程。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component("jobLogArchiveHandler")
@RequiredArgsConstructor
public class JobLogArchiveHandler implements JobHandler {

    /** 归档文件名前缀 */
    private static final String FILE_NAME_PREFIX = "jobLog-archive-";

    /** 归档文件名扩展名 */
    private static final String FILE_NAME_SUFFIX = ".csv";

    /** 归档文件名时间戳格式 */
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 无待归档数据时的返回摘要 */
    private static final String EMPTY_MESSAGE = "无可归档的 job_log";

    private final JobLogMapper jobLogMapper;

    private final JobProperties jobProperties;

    private final JobArchiveClient jobArchiveClient;

    @Override
    public String execute(String param) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(jobProperties.getLogRetentionDays());
        List<JobLog> logs = selectExpiredBatch(threshold);
        if (logs.isEmpty()) {
            log.info(EMPTY_MESSAGE);
            return EMPTY_MESSAGE;
        }
        return archive(logs);
    }

    /**
     * 查询一批待归档的过期执行日志
     *
     * @param threshold 过期阈值（{@code startTime} 早于该值即过期）
     * @return 待归档日志列表，最多 {@code archiveBatchSize} 条
     */
    private List<JobLog> selectExpiredBatch(LocalDateTime threshold) {
        return jobLogMapper.selectList(new LambdaQueryWrapper<JobLog>()
                .lt(JobLog::getStartTime, threshold)
                .orderByAsc(JobLog::getId)
                .last("LIMIT " + jobProperties.getArchiveBatchSize()));
    }

    /**
     * 导出并上传一批日志，上传成功后逻辑删除该批
     *
     * @param logs 待归档日志
     * @return 归档结果摘要（成功回执或失败摘要）
     */
    private String archive(List<JobLog> logs) {
        String fileName = FILE_NAME_PREFIX
                + LocalDateTime.now().format(FILE_NAME_FORMATTER) + FILE_NAME_SUFFIX;
        String csv = JobLogCsvWriter.write(logs);
        try {
            FileUploadVO uploaded = jobArchiveClient.upload(fileName, csv.getBytes(StandardCharsets.UTF_8));
            List<Long> ids = logs.stream().map(JobLog::getId).toList();
            jobLogMapper.deleteByIds(ids);
            String message = "job_log 归档完成：上传对象 " + uploaded.getBucket() + "/" + uploaded.getObjectKey()
                    + "，清理 " + ids.size() + " 条";
            log.info(message);
            return message;
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String message = "归档失败，本批未清理：" + reason;
            log.warn(message, e);
            return message;
        }
    }
}
