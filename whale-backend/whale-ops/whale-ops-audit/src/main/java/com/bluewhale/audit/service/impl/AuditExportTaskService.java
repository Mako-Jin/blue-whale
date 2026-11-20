package com.bluewhale.audit.service.impl;

import com.bluewhale.audit.dto.AuditLogQueryRequest;
import com.bluewhale.audit.service.AuditLogService;
import com.bluewhale.audit.support.AuditMetrics;
import com.bluewhale.audit.vo.AuditExportTaskVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 审计日志异步导出任务管理（AUTH-08）。
 *
 * <p>提交后在工作线程执行实际的「查询 → 生成 CSV → 上传 whale-file」流程，
 * HTTP 线程立即返回 {@code taskId}，调用方轮询任务状态，避免 5 万行同步导出触发网关 60s 超时。</p>
 *
 * <p>任务状态保存在进程内存（按 TTL 清理），不落库：导出结果本身已在 whale-file，
 * 重启后仅丢失任务进度，重新提交即可。并发任务数有上限，超限拒绝提交，防止线程与内存被打满。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class AuditExportTaskService {

    /** 同时执行导出任务的线程数 */
    private static final int EXPORT_THREADS = 2;

    /** 内存中保留的任务数上限，超出后拒绝新任务 */
    private static final int MAX_TASKS = 200;

    /** 任务状态保留时长（自创建起算），过期后自动清理 */
    private static final long TASK_TTL_HOURS = 2L;

    /** 失败响应统一文案（详细原因只进日志） */
    private static final String FAIL_MESSAGE = "审计日志导出失败，请稍后重试";

    /** 上传结果中的行数字段（由导出服务附加） */
    private static final String FIELD_ROWS = "rows";

    private final AuditLogService auditLogService;

    private final Executor executor;

    private final AuditMetrics auditMetrics;

    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();

    /**
     * 生产构造：使用固定大小守护线程池执行导出
     *
     * @param auditLogService 审计查询/导出的实际执行服务
     * @param auditMetrics    导出耗时/行数指标
     */
    public AuditExportTaskService(AuditLogService auditLogService, AuditMetrics auditMetrics) {
        this(auditLogService, newExportExecutor(), auditMetrics);
    }

    /**
     * 可注入执行器的构造（单测用同步执行器验证状态流转）
     *
     * @param auditLogService 审计查询/导出的实际执行服务
     * @param executor        任务执行器
     * @param auditMetrics    导出耗时/行数指标
     */
    AuditExportTaskService(AuditLogService auditLogService, Executor executor, AuditMetrics auditMetrics) {
        this.auditLogService = auditLogService;
        this.executor = executor;
        this.auditMetrics = auditMetrics;
    }

    /**
     * 提交异步导出任务
     *
     * @param request 审计日志查询条件
     * @return 任务快照（{@code status=PENDING}）
     * @throws BizException 内存任务数达到上限时拒绝
     */
    public AuditExportTaskVO submit(AuditLogQueryRequest request) {
        purgeExpired();
        if (tasks.size() >= MAX_TASKS) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "导出任务过多，请稍后重试");
        }
        String taskId = UUID.randomUUID().toString().replace("-", "");
        TaskState state = new TaskState();
        tasks.put(taskId, state);
        executor.execute(() -> run(taskId, state, request));
        return toVo(taskId, state);
    }

    /**
     * 查询导出任务状态
     *
     * @param taskId 任务ID
     * @return 任务快照
     * @throws BizException 任务不存在（不存在或已过期清理）
     */
    public AuditExportTaskVO get(String taskId) {
        purgeExpired();
        TaskState state = tasks.get(taskId);
        if (state == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toVo(taskId, state);
    }

    /**
     * 关闭导出线程池（仅当执行器为线程池时）
     */
    @PreDestroy
    public void shutdown() {
        if (executor instanceof ExecutorService executorService) {
            executorService.shutdown();
        }
    }

    /**
     * 执行导出并回写任务状态
     *
     * @param taskId  任务ID
     * @param state   任务状态
     * @param request 查询条件
     */
    private void run(String taskId, TaskState state, AuditLogQueryRequest request) {
        state.status = AuditExportTaskVO.Status.RUNNING;
        long startNanos = System.nanoTime();
        boolean success = false;
        try {
            Result<Map<String, Object>> result = auditLogService.exportCsv(request);
            if (result != null && result.getCode() == ErrorCode.SUCCESS.getCode()) {
                state.upload = result.getData();
                state.rows = extractRows(result.getData());
                state.status = AuditExportTaskVO.Status.SUCCESS;
                success = true;
                log.info("审计日志异步导出完成: taskId={}, rows={}", taskId, state.rows);
            } else {
                state.status = AuditExportTaskVO.Status.FAILED;
                state.errorMessage = result == null ? FAIL_MESSAGE : result.getMessage();
                log.warn("审计日志异步导出失败: taskId={}, message={}",
                        taskId, result == null ? "null result" : result.getMessage());
            }
        } catch (Exception e) {
            state.status = AuditExportTaskVO.Status.FAILED;
            state.errorMessage = FAIL_MESSAGE;
            log.warn("审计日志异步导出异常（受控失败）: taskId={}, reason={}", taskId, e.getMessage(), e);
        } finally {
            state.finishedAt = LocalDateTime.now();
            int rows = state.rows == null ? 0 : state.rows;
            auditMetrics.recordExport(success, rows, System.nanoTime() - startNanos);
        }
    }

    /**
     * 从上传结果中提取导出行数
     *
     * @param data 上传结果（可空）
     * @return 行数；无法解析时返回 {@code null}
     */
    private static Integer extractRows(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Object rows = data.get(FIELD_ROWS);
        return rows instanceof Number number ? number.intValue() : null;
    }

    /**
     * 清理超过 TTL 的任务，抑制内存无限增长
     */
    private void purgeExpired() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(TASK_TTL_HOURS);
        Iterator<Map.Entry<String, TaskState>> iterator = tasks.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().createdAt.isBefore(threshold)) {
                iterator.remove();
            }
        }
    }

    /**
     * 组装任务快照
     *
     * @param taskId 任务ID
     * @param state  任务状态
     * @return 任务快照
     */
    private static AuditExportTaskVO toVo(String taskId, TaskState state) {
        AuditExportTaskVO vo = new AuditExportTaskVO();
        vo.setTaskId(taskId);
        vo.setStatus(state.status);
        vo.setRows(state.rows);
        vo.setUpload(state.upload);
        vo.setErrorMessage(state.errorMessage);
        vo.setCreatedAt(state.createdAt);
        vo.setFinishedAt(state.finishedAt);
        return vo;
    }

    /**
     * 创建导出守护线程池（避免阻止 JVM 退出）
     *
     * @return 固定大小线程池
     */
    private static ExecutorService newExportExecutor() {
        AtomicInteger sequence = new AtomicInteger(1);
        return Executors.newFixedThreadPool(EXPORT_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "audit-export-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 任务内存状态（volatile 字段由工作线程写入、HTTP 线程读取）
     */
    private static final class TaskState {

        /** 任务创建时间 */
        private final LocalDateTime createdAt = LocalDateTime.now();

        /** 当前状态 */
        private volatile AuditExportTaskVO.Status status = AuditExportTaskVO.Status.PENDING;

        /** 导出行数 */
        private volatile Integer rows;

        /** 上传结果 */
        private volatile Map<String, Object> upload;

        /** 失败原因 */
        private volatile String errorMessage;

        /** 完成时间 */
        private volatile LocalDateTime finishedAt;
    }
}
