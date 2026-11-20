package com.bluewhale.audit.retention;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bluewhale.audit.retention.entity.AuditJobHeartbeat;
import com.bluewhale.audit.retention.mapper.AuditJobHeartbeatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计作业心跳记录器（等保 2.0 8.1.4.3 b：审计记录应受到保护）。
 *
 * <p>把「作业跑了没有」落成库里的客观事实：每轮执行先写 {@code last_start_at}，
 * 结束时按结果写 {@code SUCCESS} / {@code FAILED}。判定陈旧只用 {@code last_success_at}——
 * 一直失败和一概没跑，对合规取证是同一件事：这段时间没有产生可信的审计留存证据。</p>
 *
 * <p><b>失败不能反噬主任务</b>：心跳是观测手段，库写不进去只记 WARN，
 * 绝不让一次归档/巡检因为「记不上心跳」而失败。</p>
 *
 * <p>列名一律用字符串形式（{@link UpdateWrapper}）而非 Lambda：Lambda 列名是<b>立即</b>解析的，
 * 无 Spring 上下文的单测会抛 {@code can not find lambda cache}；本类还要靠 {@code setSql}
 * 做 {@code run_count = run_count + 1} 的原子自增，本来就走字符串路径。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditJobHeartbeatRecorder {

    /** 最近一次执行成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** 最近一次执行失败 */
    public static final String STATUS_FAILURE = "FAILED";

    /** 最近一次执行仍在进行中（进程被杀时会停在这个状态，本身就是异常信号） */
    public static final String STATUS_RUNNING = "RUNNING";

    /** {@code last_message} 列宽 */
    private static final int MESSAGE_MAX_LENGTH = 512;

    private final AuditJobHeartbeatMapper heartbeatMapper;

    /**
     * 记录作业开始
     *
     * <p>累计次数在此自增；作业中途被 kill 会留下 {@code RUNNING} 状态，
     * 结合 {@code last_success_at} 未推进即可判断「跑了但没跑完」。</p>
     *
     * @param jobCode 任务编码，如 {@code audit.log.retention}
     */
    public void recordStart(String jobCode) {
        if (isBlank(jobCode)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            UpdateWrapper<AuditJobHeartbeat> update = new UpdateWrapper<>();
            update.eq("job_code", jobCode)
                    .setSql("run_count = run_count + 1")
                    .set("last_start_at", now)
                    .set("last_status", STATUS_RUNNING);
            if (heartbeatMapper.update(null, update) > 0) {
                return;
            }
            AuditJobHeartbeat heartbeat = new AuditJobHeartbeat();
            heartbeat.setJobCode(jobCode);
            heartbeat.setLastStartAt(now);
            heartbeat.setLastStatus(STATUS_RUNNING);
            heartbeat.setRunCount(1L);
            heartbeat.setFailCount(0L);
            heartbeatMapper.insert(heartbeat);
        } catch (Exception e) {
            log.warn("审计作业心跳写入失败（不影响任务执行）: job={}, phase=start, reason={}",
                    jobCode, e.getMessage());
        }
    }

    /**
     * 记录作业成功
     *
     * @param jobCode 任务编码
     * @param message 执行摘要（超长截断，可传 {@code null}）
     */
    public void recordSuccess(String jobCode, String message) {
        finish(jobCode, STATUS_SUCCESS, message, false);
    }

    /**
     * 记录作业失败
     *
     * @param jobCode 任务编码
     * @param message 异常简述（超长截断，可传 {@code null}）
     */
    public void recordFailure(String jobCode, String message) {
        finish(jobCode, STATUS_FAILURE, message, true);
    }

    /**
     * 写入结束状态
     *
     * <p>成功才推进 {@code last_success_at}，失败只加 {@code fail_count}——否则「一直在跑且一直失败」
     * 会被心跳伪装成健康。</p>
     *
     * @param jobCode    任务编码
     * @param status     结果
     * @param message    摘要
     * @param failed     是否失败（决定是否自增失败计数）
     */
    private void finish(String jobCode, String status, String message, boolean failed) {
        if (isBlank(jobCode)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            UpdateWrapper<AuditJobHeartbeat> update = new UpdateWrapper<>();
            update.eq("job_code", jobCode)
                    .set("last_end_at", now)
                    .set("last_status", status)
                    .set("last_message", truncate(message));
            if (failed) {
                update.setSql("fail_count = fail_count + 1");
            } else {
                update.set("last_success_at", now);
            }
            if (heartbeatMapper.update(null, update) > 0) {
                return;
            }
            // 没有 start 记录（首次即失败，或 start 写入失败）：补一行，保证失败同样留痕
            AuditJobHeartbeat heartbeat = new AuditJobHeartbeat();
            heartbeat.setJobCode(jobCode);
            heartbeat.setLastStartAt(now);
            heartbeat.setLastEndAt(now);
            heartbeat.setLastStatus(status);
            heartbeat.setLastMessage(truncate(message));
            heartbeat.setRunCount(1L);
            heartbeat.setFailCount(failed ? 1L : 0L);
            if (!failed) {
                heartbeat.setLastSuccessAt(now);
            }
            heartbeatMapper.insert(heartbeat);
        } catch (Exception e) {
            log.warn("审计作业心跳写入失败（不影响任务结果）: job={}, status={}, reason={}",
                    jobCode, status, e.getMessage());
        }
    }

    /**
     * 截断摘要到列宽
     *
     * @param message 原始摘要
     * @return 截断后的摘要；入参为空时返回 {@code null}
     */
    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MESSAGE_MAX_LENGTH
                ? message : message.substring(0, MESSAGE_MAX_LENGTH);
    }

    /**
     * 判断任务编码是否为空
     *
     * @param value 任务编码
     * @return 空白返回 {@code true}
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
