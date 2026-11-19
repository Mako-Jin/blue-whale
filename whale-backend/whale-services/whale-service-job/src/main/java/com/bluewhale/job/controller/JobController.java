package com.bluewhale.job.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.job.dto.JobAlertQueryRequest;
import com.bluewhale.job.dto.JobBatchDeleteRequest;
import com.bluewhale.job.dto.JobBatchStatusRequest;
import com.bluewhale.job.dto.JobDependencySetRequest;
import com.bluewhale.job.dto.JobImportRequest;
import com.bluewhale.job.dto.JobInfoCreateRequest;
import com.bluewhale.job.dto.JobInfoQueryRequest;
import com.bluewhale.job.dto.JobInfoUpdateRequest;
import com.bluewhale.job.dto.JobLogQueryRequest;
import com.bluewhale.job.dto.JobLogStatsQueryRequest;
import com.bluewhale.job.dto.JobStatusRequest;
import com.bluewhale.job.service.JobAlertService;
import com.bluewhale.job.service.JobBatchService;
import com.bluewhale.job.service.JobDependencyService;
import com.bluewhale.job.service.JobGraphService;
import com.bluewhale.job.service.JobInfoService;
import com.bluewhale.job.service.JobLogService;
import com.bluewhale.job.vo.ExecutorLogVO;
import com.bluewhale.job.vo.JobAlertVO;
import com.bluewhale.job.vo.JobBatchResultVO;
import com.bluewhale.job.vo.JobDependencyVO;
import com.bluewhale.job.vo.JobExportVO;
import com.bluewhale.job.vo.JobGraphValidateVO;
import com.bluewhale.job.vo.JobGraphVO;
import com.bluewhale.job.vo.JobImportResultVO;
import com.bluewhale.job.vo.JobInfoVO;
import com.bluewhale.job.vo.JobLogStatsVO;
import com.bluewhale.job.vo.JobLogVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 定时任务管理接口。
 *
 * <p>服务内路径 {@code /job/v1/**}（REQ-ARCH-003 网关不剥前缀，故网关按 {@code Path=/job/**}
 * 转发后外部路径与本文档一致）；内网执行器协议 {@code /job/internal/executor/**} 不参与版本化。
 * 全部接口返回统一响应体 {@link Result}，受 {@code @RequirePermission("sys:job:*")} 保护
 * （AUTH-06：由网关权限码头 {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定），
 * 写操作均标注 {@link AuditLog} 留痕。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/job/v1")
@RequiredArgsConstructor
public class JobController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "job_info";

    /** 导出文件名时间戳格式 */
    private static final DateTimeFormatter EXPORT_FILE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JobInfoService jobInfoService;

    private final JobLogService jobLogService;

    private final JobDependencyService jobDependencyService;

    private final JobAlertService jobAlertService;

    private final JobGraphService jobGraphService;

    private final JobBatchService jobBatchService;

    private final ObjectMapper objectMapper;

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

    /** 批量删除任务：逻辑删除，逐条处理，部分失败不影响其余 */
    @PostMapping("/info/batch-delete")
    @RequirePermission("sys:job:delete")
    @AuditLog(operation = "批量删除定时任务", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<JobBatchResultVO> batchDelete(@Valid @RequestBody JobBatchDeleteRequest request) {
        return Result.ok(jobBatchService.batchDelete(request));
    }

    /** 批量启停任务：逐条处理，部分失败不影响其余 */
    @PostMapping("/info/batch-status")
    @RequirePermission("sys:job:edit")
    @AuditLog(operation = "批量启停定时任务", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<JobBatchResultVO> batchStatus(@Valid @RequestBody JobBatchStatusRequest request) {
        return Result.ok(jobBatchService.batchStatus(request));
    }

    /** 导出任务定义与依赖边：可按分组与任务ID过滤，依赖以业务标识表达 */
    @GetMapping("/info/export")
    @RequirePermission("sys:job:list")
    public Result<JobExportVO> export(@RequestParam(required = false) String jobGroup,
                                      @RequestParam(required = false) List<Long> jobIds) {
        return Result.ok(jobBatchService.export(jobGroup, jobIds));
    }

    /** 下载导出文件：内容与导出接口一致，带附件响应头便于直接另存后导入 */
    @GetMapping("/info/export/download")
    @RequirePermission("sys:job:list")
    public ResponseEntity<byte[]> download(@RequestParam(required = false) String jobGroup,
                                           @RequestParam(required = false) List<Long> jobIds) {
        JobExportVO export = jobBatchService.export(jobGroup, jobIds);
        String fileName = "job-export-" + LocalDateTime.now().format(EXPORT_FILE_FORMATTER) + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsBytes(export));
    }

    /** 导入任务定义与依赖边：冲突策略 SKIP / OVERWRITE，逐条处理并聚合失败明细 */
    @PostMapping("/info/import")
    @RequirePermission("sys:job:add")
    @AuditLog(operation = "导入定时任务", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<JobImportResultVO> importJobs(@Valid @RequestBody JobImportRequest request) {
        return Result.ok(jobBatchService.importJobs(request));
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

    /** 执行统计：按天趋势与失败/耗时 TopN */
    @GetMapping("/log/stats")
    @RequirePermission("sys:job:log:list")
    public Result<JobLogStatsVO> logStats(JobLogStatsQueryRequest request) {
        return Result.ok(jobLogService.stats(request));
    }

    /**
     * 回捞某次执行在执行器侧保存的完整日志。
     *
     * <p>执行流水的 result / error_msg 落库截断到 2000 字，完整堆栈与业务日志只在执行器本地；
     * 本接口按流水里记录的实例地址精确回捞。</p>
     */
    @GetMapping("/log/{id}/detail")
    @RequirePermission("sys:job:log:list")
    public Result<ExecutorLogVO> logDetail(@PathVariable Long id) {
        return Result.ok(jobLogService.executorLog(id));
    }

    /** 查询任务依赖关系（上游与下游） */
    @GetMapping("/info/{id}/dependencies")
    @RequirePermission("sys:job:list")
    public Result<JobDependencyVO> dependencies(@PathVariable Long id) {
        return Result.ok(jobDependencyService.getDependencies(id));
    }

    /** 全量替换任务的上游依赖（保存后按是否存在上游重新调度） */
    @PostMapping("/info/{id}/dependencies")
    @RequirePermission("sys:job:edit")
    @AuditLog(operation = "配置定时任务依赖", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<JobDependencyVO> saveDependencies(@PathVariable Long id,
                                                    @Valid @RequestBody JobDependencySetRequest request) {
        return Result.ok(jobDependencyService.saveDependencies(id, request));
    }

    /** 查询任务依赖拓扑图（可选按分组过滤） */
    @GetMapping("/graph")
    @RequirePermission("sys:job:list")
    public Result<JobGraphVO> graph(@RequestParam(required = false) String jobGroup) {
        return Result.ok(jobGraphService.graph(jobGroup));
    }

    /** 全量节点环检测：供保存前校验与数据体检 */
    @GetMapping("/graph/validate")
    @RequirePermission("sys:job:list")
    public Result<JobGraphValidateVO> validateGraph() {
        return Result.ok(jobGraphService.validate());
    }

    /** 分页查询任务失败告警记录 */
    @GetMapping("/alert/page")
    @RequirePermission("sys:job:log:list")
    public Result<PageResult<JobAlertVO>> alertPage(JobAlertQueryRequest request) {
        return Result.ok(jobAlertService.page(request));
    }
}
