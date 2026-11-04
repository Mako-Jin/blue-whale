package com.bluewhale.erp.analytics.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.analytics.application.service.AnalyticsAppService;
import com.bluewhale.erp.analytics.application.view.CubeResultView;
import com.bluewhale.erp.analytics.domain.model.AnalyticsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 多维分析接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/bi/cube")
@RequiredArgsConstructor
public class AnalyticsCubeController {

    private final AnalyticsAppService analyticsAppService;

    /**
     * 多维聚合查询。
     *
     * @param query 查询条件（维度、指标、日期区间）
     * @return 聚合结果
     */
    @GetMapping
    @RequirePermission("sys:erp:bi:cube:query")
    public Result<CubeResultView> cube(@ModelAttribute AnalyticsQuery query) {
        return Result.ok(analyticsAppService.cube(query));
    }
}
