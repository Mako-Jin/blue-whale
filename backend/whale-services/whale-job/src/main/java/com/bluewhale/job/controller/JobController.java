package com.bluewhale.job.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.job.dto.JobInfoCreateRequest;
import com.bluewhale.job.dto.JobInfoQueryRequest;
import com.bluewhale.job.dto.JobInfoUpdateRequest;
import com.bluewhale.job.dto.JobLogQueryRequest;
import com.bluewhale.job.dto.JobStatusRequest;
import com.bluewhale.job.service.JobInfoService;
import com.bluewhale.job.service.JobLogService;
import com.bluewhale.job.vo.JobInfoVO;
import com.bluewhale.job.vo.JobLogVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定时任务管理接口。
 *
 * <p>服务内路径 {@code /job/**}；经网关按 {@code Path=/job/**} 规则转发，外部路径与本文档一致。
 * 全部接口返回统一响应体 {@link Result}，受 {@code @RequirePermission("sys:job:*")} 保护
 * （AUTH-06：由网关权限码头 {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定），
 * 写操作均标注 {@link AuditLog} 留痕。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/job")
@RequiredArgsConstructor
public class JobController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "job_info";

    private final JobInfoService jobInfoService;

    private final JobLogService jobLogService;

    /** 分页查询定时任务 */
    @GetMapping("/info/page")
    @RequirePermission("sys:job:list")
    public Result<PageResult<JobInfoVO>> page(JobInfoQueryRequest request) {
        return Result.ok(jobInfoService.page(request));
    }

    /** 任务详情 */
    @GetMapping("/info/{id}")
    @RequirePermission("sys:job:list")
    public Result<JobInfoVO> detail(@PathVariable Long id) {
        return Result.ok(jobInfoService.getDetail(id));
    }

    /** 新增任务 */
    @PostMapping("/info")
    @RequirePermission("sys:job:add")
    @AuditLog(operation = "新增定时任务", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<JobInfoVO> create(@Valid @RequestBody JobInfoCreateRequest request) {
        return Result.ok(jobInfoService.create(request));
    }

    /** 更新任务 */
    @PutMapping("/info/{id}")
    @RequirePermission("sys:job:edit")
    @AuditLog(operation = "更新定时任务", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<JobInfoVO> update(@PathVariable Long id, @Valid @RequestBody JobInfoUpdateRequest request) {
        return Result.ok(jobInfoService.update(id, request));
    }

    /** 删除任务 */
    @DeleteMapping("/info/{id}")
    @RequirePermission("sys:job:delete")
    @AuditLog(operation = "删除定时任务", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        jobInfoService.delete(id);
        return Result.ok();
    }

    /** 启停任务：变更调度状态属敏感操作，必留痕 */
    @PutMapping("/info/{id}/status")
    @RequirePermission("sys:job:edit")
    @AuditLog(operation = "启停定时任务", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody JobStatusRequest request) {
        jobInfoService.changeStatus(id, request.getStatus());
        return Result.ok();
    }

    /** 立即执行一次 */
    @PostMapping("/info/{id}/run")
    @RequirePermission("sys:job:run")
    @AuditLog(operation = "立即执行定时任务", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> run(@PathVariable Long id) {
        jobInfoService.runOnce(id);
        return Result.ok();
    }

    /** 分页查询任务执行日志 */
    @GetMapping("/log/page")
    @RequirePermission("sys:job:log:list")
    public Result<PageResult<JobLogVO>> logPage(JobLogQueryRequest request) {
        return Result.ok(jobLogService.page(request));
    }
}
