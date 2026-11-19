package com.bluewhale.job.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.job.dto.ExecutorBeatRequest;
import com.bluewhale.job.dto.ExecutorRegisterRequest;
import com.bluewhale.job.service.JobExecutorService;
import com.bluewhale.job.vo.JobExecutorVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 执行器注册内网接口。
 *
 * <p>路径前缀 {@code /job/internal}，仅业务服务经 Nacos 内网直连调用；
 * 网关 {@code internal-deny} 对 {@code /job/internal/**} 返回 403，外部不可达，
 * 且 {@code InternalTokenFilter} 会校验内网令牌。</p>
 *
 * <p>租户约定：调用方（执行器注册器）在请求头携带 {@code X-Tenant-Id}，
 * 服务端由 {@code TenantContextFilter} 建立租户上下文；未携带时回落默认租户。</p>
 *
 * @author BlueWhale
 */
@Validated
@RestController
@RequestMapping("/job/internal/executor")
@RequiredArgsConstructor
public class JobExecutorInternalController {

    private final JobExecutorService jobExecutorService;

    /**
     * 注册执行器实例。
     *
     * @param request 注册请求
     * @return 空结果
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody ExecutorRegisterRequest request) {
        jobExecutorService.register(request);
        return Result.ok();
    }

    /**
     * 执行器心跳续约。
     *
     * @param request 心跳请求
     * @return {@code true} 表示续约成功；{@code false} 表示实例已不存在，调用方应重新注册
     */
    @PutMapping("/beat")
    public Result<Boolean> beat(@Valid @RequestBody ExecutorBeatRequest request) {
        return Result.ok(jobExecutorService.beat(request));
    }

    /**
     * 注销执行器实例（优雅停机）。
     *
     * @param request 注销请求
     * @return 空结果
     */
    @DeleteMapping("/register")
    public Result<Void> deregister(@Valid @RequestBody ExecutorBeatRequest request) {
        jobExecutorService.deregister(request);
        return Result.ok();
    }

    /**
     * 查询执行器列表（含离线实例）。
     *
     * @param appName 执行器应用名，为空时查询当前租户全部
     * @return 执行器列表
     */
    @GetMapping("/list")
    public Result<List<JobExecutorVO>> list(
            @RequestParam(required = false) @Size(max = 64, message = "{validation.job.executor.app-name-size}") String appName) {
        return Result.ok(jobExecutorService.list(TenantContext.get(), appName));
    }
}
