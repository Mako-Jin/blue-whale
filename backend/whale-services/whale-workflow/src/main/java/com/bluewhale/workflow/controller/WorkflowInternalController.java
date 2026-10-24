package com.bluewhale.workflow.controller;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.workflow.service.WorkflowArchiveReadService;
import com.bluewhale.workflow.vo.ApprovedInstanceVO;
import com.bluewhale.workflow.vo.ArchiveInstanceDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 流程实例归档内网接口（只读）。
 *
 * <p>服务内路径前缀 {@code /workflow/internal}，供档案服务（whale-archive）增量拉取已审批实例；
 * 网关 {@code internal-deny} 对 {@code /workflow/internal/**} 统一返回 403，外部不可达，故不加权限码注解。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/workflow/internal/instance")
@RequiredArgsConstructor
public class WorkflowInternalController {

    private final WorkflowArchiveReadService workflowArchiveReadService;

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
