package com.bluewhale.pm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.pm.application.service.ReportAppService;
import com.bluewhale.pm.interfaces.vo.BurndownVO;
import com.bluewhale.pm.interfaces.vo.CumulativeFlowVO;
import com.bluewhale.pm.interfaces.vo.VelocityVO;
import com.bluewhale.pm.interfaces.vo.WorkloadVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 报表接口：燃尽、速率、累积流与成员负载（只读，不审计）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pm/v{version}/report", version = "1")
@RequiredArgsConstructor
public class ReportController {

    /** 报表查看权限码 */
    private static final String PERMISSION_VIEW = "sys:pm:report:view";

    private final ReportAppService reportAppService;

    /**
     * 迭代燃尽图
     *
     * @param projectId  项目ID
     * @param sprintId   迭代ID，为空时取进行中迭代
     * @param userHeader 用户ID请求头
     * @return 燃尽图数据
     */
    @GetMapping("/burndown")
    @RequirePermission(PERMISSION_VIEW)
    public Result<BurndownVO> burndown(@RequestParam Long projectId,
            @RequestParam(required = false) Long sprintId,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(reportAppService.burndown(projectId, sprintId, operatorId(userHeader)));
    }

    /**
     * 迭代速率图
     *
     * @param projectId  项目ID
     * @param limit      迭代数量，默认 5，上限 12
     * @param userHeader 用户ID请求头
     * @return 速率图数据
     */
    @GetMapping("/velocity")
    @RequirePermission(PERMISSION_VIEW)
    public Result<VelocityVO> velocity(@RequestParam Long projectId,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(reportAppService.velocity(projectId, limit, operatorId(userHeader)));
    }

    /**
     * 累积流图
     *
     * @param projectId  项目ID
     * @param sprintId   迭代ID，为空时统计整个项目
     * @param startDate  统计开始日期（ISO 格式）
     * @param endDate    统计结束日期（ISO 格式）
     * @param userHeader 用户ID请求头
     * @return 累积流图数据
     */
    @GetMapping("/cumulative-flow")
    @RequirePermission(PERMISSION_VIEW)
    public Result<CumulativeFlowVO> cumulativeFlow(@RequestParam Long projectId,
            @RequestParam(required = false) Long sprintId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(reportAppService.cumulativeFlow(projectId, sprintId, startDate, endDate,
                operatorId(userHeader)));
    }

    /**
     * 成员负载图
     *
     * @param projectId  项目ID
     * @param userHeader 用户ID请求头
     * @return 成员负载数据
     */
    @GetMapping("/workload")
    @RequirePermission(PERMISSION_VIEW)
    public Result<WorkloadVO> workload(@RequestParam Long projectId,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(reportAppService.workload(projectId, operatorId(userHeader)));
    }

    /**
     * 解析网关注入的用户ID请求头（不可解析时返回 {@code null}，由应用服务按未认证处理）
     *
     * @param userHeader 用户ID请求头
     * @return 用户ID；缺失或非法时返回 {@code null}
     */
    private static Long operatorId(String userHeader) {
        if (userHeader == null || userHeader.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userHeader.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
