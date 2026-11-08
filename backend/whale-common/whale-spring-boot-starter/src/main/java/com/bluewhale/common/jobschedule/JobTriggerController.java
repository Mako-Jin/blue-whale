package com.bluewhale.common.jobschedule;

import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

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
 * @author BlueWhale
 */
@Slf4j
@RestController
@RequestMapping("/internal/job")
@RequiredArgsConstructor
public class JobTriggerController {

    /** 参数错误业务码 */
    private static final int CODE_BAD_REQUEST = 400;

    /** 执行失败业务码 */
    private static final int CODE_EXECUTION_FAILED = 500;

    private final RemoteJobRegistry registry;

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
        long start = System.currentTimeMillis();
        try {
            String result = registry.invoke(code);
            log.info("远程触发任务完成: code={}, costMs={}", code, System.currentTimeMillis() - start);
            return Result.ok(result);
        } catch (RuntimeException e) {
            log.error("远程触发任务失败: code={}, costMs={}", code, System.currentTimeMillis() - start, e);
            return Result.fail(CODE_EXECUTION_FAILED, e.getMessage());
        }
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
