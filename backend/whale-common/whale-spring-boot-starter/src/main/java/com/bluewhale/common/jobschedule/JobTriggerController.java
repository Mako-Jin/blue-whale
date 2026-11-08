package com.bluewhale.common.jobschedule;

import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * 受管任务的内网触发端点：供调度中心（whale-job）远程触发。
 *
 * <p>路径含 {@code /internal/}，由内网令牌过滤器校验 {@code X-Internal-Token}，
 * 且网关 {@code internal-deny} 对外部请求返回 403。默认**不注册**，
 * 仅当 {@code whale.schedule.trigger-enabled=true} 时由自动配置装配。</p>
 *
 * <p>任务编码含点号（如 {@code crm.analytics.snapshot}），路径变量使用 {@code .+} 精确匹配，
 * 避免被当作文件扩展名截断。</p>
 *
 * <p>每次触发都会绑定执行批次ID到 MDC，并在结束时把本次执行线程内的日志落盘；
 * 调度中心可经 {@code GET /internal/job/{code}/log/latest} 回捞最近一次执行的日志。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@RestController
@RequestMapping("/internal/job")
@RequiredArgsConstructor
public class JobTriggerController {

    /** 参数错误业务码 */
    private static final int CODE_BAD_REQUEST = 400;

    /** 无执行日志业务码 */
    private static final int CODE_NOT_FOUND = 404;

    /** 执行失败业务码 */
    private static final int CODE_EXECUTION_FAILED = 500;

    private final RemoteJobRegistry registry;

    private final JobExecutionLogStore logStore;

    /**
     * 触发指定任务。
     *
     * <p>失败时返回「HTTP 200 + 非 0 业务码」而非 5xx：调度侧统一解析业务码判定成败，
     * 保留错误信息的同时不影响其重试决策。</p>
     *
     * @param code 任务编码
     * @return 执行摘要
     */
    @PostMapping("/{code:.+}/run")
    public Result<String> run(@PathVariable String code) {
        if (!registry.contains(code)) {
            return Result.fail(CODE_BAD_REQUEST, "未注册的任务编码: " + code);
        }
        String runId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(JobExecutionLogStore.MDC_RUN_ID, runId);
        MDC.put(JobExecutionLogStore.MDC_JOB_CODE, code);
        logStore.begin(runId, code);
        long start = System.currentTimeMillis();
        String outcome = "未完成";
        try {
            String result = registry.invoke(code);
            outcome = "成功, 摘要=" + result;
            log.info("远程触发任务完成: code={}, costMs={}", code, System.currentTimeMillis() - start);
            return Result.ok(result);
        } catch (RuntimeException e) {
            outcome = "失败, 原因=" + e.getMessage();
            log.error("远程触发任务失败: code={}, costMs={}", code, System.currentTimeMillis() - start, e);
            return Result.fail(CODE_EXECUTION_FAILED, e.getMessage());
        } finally {
            logStore.finish(runId, code, outcome);
            MDC.remove(JobExecutionLogStore.MDC_RUN_ID);
            MDC.remove(JobExecutionLogStore.MDC_JOB_CODE);
        }
    }

    /**
     * 回捞指定任务最近一次执行的日志。
     *
     * <p>调度中心在任务失败后调用，用于把业务侧完整日志（含堆栈）带进执行流水，
     * 避免排障时再去翻容器 stdout 并按时间轴对齐。</p>
     *
     * @param code 任务编码
     * @return 日志文本；该任务从未在远程触发下执行过时返回 404 业务码
     */
    @GetMapping("/{code:.+}/log/latest")
    public Result<String> latestLog(@PathVariable String code) {
        String content = logStore.readLatest(code);
        if (content == null) {
            return Result.fail(CODE_NOT_FOUND, "无执行日志: " + code);
        }
        return Result.ok(content);
    }

    /**
     * 列出本实例已登记的任务编码（供调度侧核对能力，不触发执行）。
     *
     * @return 任务编码集合
     */
    @GetMapping("/codes")
    public Result<Set<String>> codes() {
        return Result.ok(registry.codes());
    }
}
