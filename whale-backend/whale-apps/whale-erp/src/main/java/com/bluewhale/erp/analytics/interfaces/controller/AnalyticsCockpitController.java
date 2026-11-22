package com.bluewhale.erp.analytics.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.analytics.application.command.CockpitRefreshCommand;
import com.bluewhale.erp.analytics.application.service.AnalyticsAppService;
import com.bluewhale.erp.analytics.application.view.CockpitView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 经营驾驶舱接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/bi/cockpit", version = "1")
@RequiredArgsConstructor
public class AnalyticsCockpitController {

    private final AnalyticsAppService analyticsAppService;

    /**
     * 查询驾驶舱指标。
     *
     * @param periodCode 统计期间 yyyyMM，可空
     * @return 驾驶舱
     */
    @GetMapping
    @RequirePermission("sys:erp:bi:cockpit:list")
    public Result<CockpitView> cockpit(
            @RequestParam(value = "periodCode", required = false) String periodCode) {
        return Result.ok(analyticsAppService.getCockpit(periodCode));
    }

    /**
     * 刷新驾驶舱快照。
     *
     * @param command 刷新命令
     * @return 驾驶舱
     */
    @PostMapping("/refresh")
    @RequirePermission("sys:erp:bi:cockpit:refresh")
    @AuditLog(operation = "刷新经营驾驶舱", targetType = "erp_bi_cockpit_snapshot", type = OperateType.UPDATE)
    public Result<CockpitView> refresh(@RequestBody CockpitRefreshCommand command) {
        return Result.ok(analyticsAppService.refreshCockpit(command));
    }

    /**
     * 主动失效指定期间快照。
     *
     * @param periodCode 统计期间
     * @return 空
     */
    @PostMapping("/invalidate")
    @RequirePermission("sys:erp:bi:cockpit:refresh")
    @AuditLog(operation = "失效驾驶舱快照", targetType = "erp_bi_cockpit_snapshot", type = OperateType.UPDATE)
    public Result<Void> invalidate(@RequestParam String periodCode) {
        analyticsAppService.invalidateCockpit(periodCode);
        return Result.ok();
    }
}
