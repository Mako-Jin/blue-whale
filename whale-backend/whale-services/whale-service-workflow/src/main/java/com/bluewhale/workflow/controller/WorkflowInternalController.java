package com.bluewhale.workflow.controller;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.workflow.request.InternalProcessStartRequest;
import com.bluewhale.workflow.request.ProcessInstanceQueryRequest;
import com.bluewhale.workflow.service.ProcessInstanceService;
import com.bluewhale.workflow.service.WorkflowArchiveReadService;
import com.bluewhale.workflow.vo.ApprovedInstanceVO;
import com.bluewhale.workflow.vo.ArchiveInstanceDetailVO;
import com.bluewhale.workflow.vo.InstanceStatusVO;
import com.bluewhale.workflow.vo.ProcessInstanceVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程实例归档内网接口（只读）。
 *
 * <p>服务内路径前缀 {@code /workflow/v1/internal}，供档案服务（whale-archive）增量拉取已审批实例；
 * 网关 {@code internal-deny} 对 {@code /workflow/v1/internal/**} 统一返回 403，外部不可达，故不加权限码注解。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/workflow/v{version}/internal/instance", version = "1")
@RequiredArgsConstructor
public class WorkflowInternalController {

    private final WorkflowArchiveReadService workflowArchiveReadService;

    private final ProcessInstanceService processInstanceService;

    /**
     * 内网按流程编码发起实例。
     *
     * @param request 发起请求
     * @return 流程实例视图对象
     */
    @PostMapping("/start")
    public Result<ProcessInstanceVO> start(@Valid @RequestBody InternalProcessStartRequest request,
                                           HttpServletRequest httpRequest) {
        if (!StringUtils.hasText(request.getInitiator())) {
            request.setInitiator(WebHeaders.username(httpRequest));
        }
        return Result.ok(processInstanceService.startByCode(request));
    }

    /**
     * 分页查询实例（支持「我发起」：{@code initiator} 精确匹配）。
     *
     * @param request 查询条件
     * @return 实例分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<ProcessInstanceVO>> page(ProcessInstanceQueryRequest request) {
        return Result.ok(processInstanceService.page(request));
    }

    /**
     * 查询实例详情（含任务轨迹）。
     *
     * @param id 流程实例ID
     * @return 实例详情
     */
    @GetMapping("/{id}")
    public Result<ProcessInstanceVO> instanceDetail(@PathVariable Long id) {
        return Result.ok(processInstanceService.getDetail(id));
    }

    /**
     * 撤销运行中的实例。
     *
     * @param id 流程实例ID
     * @return 空结果
     */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        processInstanceService.cancelInternal(id);
        return Result.ok();
    }

    /**
     * 批量查询实例状态摘要（最多 100 个）。
     *
     * @param instanceIds 实例ID列表（逗号分隔或重复参数）
     * @return 状态摘要列表
     */
    @GetMapping("/status")
    public Result<List<InstanceStatusVO>> status(@RequestParam("instanceIds") List<Long> instanceIds) {
        return Result.ok(processInstanceService.batchStatus(instanceIds));
    }

    /**
     * 按办结时间游标分页查询已通过实例。
     *
     * @param updatedAfter 游标下界（不含，ISO-8601 本地时间）
     * @param updatedBefore 游标上界（含，ISO-8601 本地时间）
     * @param current 页码，默认 1
     * @param size 每页条数，默认 100、上限 200
     * @return 已通过实例分页结果
     */
    @GetMapping("/approved")
    public Result<PageResult<ApprovedInstanceVO>> approved(
            @RequestParam(value = "updatedAfter", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedAfter,
            @RequestParam(value = "updatedBefore", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedBefore,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "100") long size) {
        return Result.ok(workflowArchiveReadService.approved(updatedAfter, updatedBefore, current, size));
    }

    /**
     * 查询实例归档详情（实例 + 定义快照 + 任务轨迹）。
     *
     * @param id 流程实例ID
     * @return 归档详情
     */
    @GetMapping("/{id}/detail")
    public Result<ArchiveInstanceDetailVO> detail(@PathVariable Long id) {
        return Result.ok(workflowArchiveReadService.detail(id));
    }
}
