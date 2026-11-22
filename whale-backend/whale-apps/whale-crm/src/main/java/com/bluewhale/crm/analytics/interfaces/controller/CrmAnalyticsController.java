package com.bluewhale.crm.analytics.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.analytics.application.service.AnalyticsAppService;
import com.bluewhale.crm.analytics.domain.model.CollectionAnalysisData;
import com.bluewhale.crm.analytics.domain.model.CustomerAnalysisData;
import com.bluewhale.crm.analytics.domain.model.DashboardData;
import com.bluewhale.crm.analytics.domain.model.FunnelViewData;
import com.bluewhale.crm.analytics.domain.model.MetricSnapshot;
import com.bluewhale.crm.analytics.domain.model.MetricSnapshotQuery;
import com.bluewhale.crm.analytics.domain.model.RankingRow;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * CRM 数据分析接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/crm/v{version}/analytics", version = "1")
@RequiredArgsConstructor
public class CrmAnalyticsController {

    private final AnalyticsAppService analyticsAppService;

    /**
     * 销售看板。
     *
     * @param request 当前请求
     * @return 看板数据
     */
    @GetMapping("/dashboard")
    @RequirePermission(CrmPermissions.ANALYTICS_DASHBOARD)
    public Result<DashboardData> dashboard(HttpServletRequest request) {
        return Result.ok(analyticsAppService.dashboard(CrmActorContext.from(request)));
    }

    /**
     * 漏斗分析。
     *
     * @param startDate 起始日
     * @param endDate 结束日
     * @param request 当前请求
     * @return 漏斗数据
     */
    @GetMapping("/funnel")
    @RequirePermission(CrmPermissions.ANALYTICS_FUNNEL)
    public Result<FunnelViewData> funnel(
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request) {
        return Result.ok(analyticsAppService.funnel(startDate, endDate, CrmActorContext.from(request)));
    }

    /**
     * 客户分析。
     *
     * @param statDate 统计日
     * @param request 当前请求
     * @return 客户分析数据
     */
    @GetMapping("/customer")
    @RequirePermission(CrmPermissions.ANALYTICS_CUSTOMER)
    public Result<CustomerAnalysisData> customer(
            @RequestParam(value = "statDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate,
            HttpServletRequest request) {
        return Result.ok(analyticsAppService.customerAnalysis(statDate, CrmActorContext.from(request)));
    }

    /**
     * 销售排行。
     *
     * @param startDate 起始日
     * @param endDate 结束日
     * @param request 当前请求
     * @return 排行
     */
    @GetMapping("/ranking")
    @RequirePermission(CrmPermissions.ANALYTICS_RANKING)
    public Result<List<RankingRow>> ranking(
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request) {
        return Result.ok(analyticsAppService.ranking(startDate, endDate, CrmActorContext.from(request)));
    }

    /**
     * 回款分析。
     *
     * @param statDate 统计日
     * @param request 当前请求
     * @return 回款分析数据
     */
    @GetMapping("/collection")
    @RequirePermission(CrmPermissions.ANALYTICS_COLLECTION)
    public Result<CollectionAnalysisData> collection(
            @RequestParam(value = "statDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate,
            HttpServletRequest request) {
        return Result.ok(analyticsAppService.collectionAnalysis(statDate, CrmActorContext.from(request)));
    }

    /**
     * 生成指标快照。
     *
     * @param statDate 统计日，可空（默认昨日）
     * @param request 当前请求
     * @return 生成条数
     */
    @PostMapping("/snapshot")
    @RequirePermission(CrmPermissions.ANALYTICS_DASHBOARD)
    @AuditLog(operation = "生成指标快照", type = OperateType.CREATE, targetType = "crm_metric_snapshot")
    public Result<Integer> generateSnapshot(
            @RequestParam(value = "statDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate,
            HttpServletRequest request) {
        return Result.ok(analyticsAppService.generateSnapshots(statDate));
    }

    /**
     * 分页查询指标快照。
     *
     * @param metricCode 指标编码
     * @param startDate 起始统计日
     * @param endDate 结束统计日
     * @param dimensionType 维度类型
     * @param dimensionId 维度值
     * @param current 当前页
     * @param pageNum 当前页（兼容参数）
     * @param size 每页条数
     * @param pageSize 每页条数（兼容参数）
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/snapshot/page")
    @RequirePermission(CrmPermissions.ANALYTICS_DASHBOARD)
    public Result<PageResult<MetricSnapshot>> snapshotPage(
            @RequestParam(value = "metricCode", required = false) String metricCode,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "dimensionType", required = false) String dimensionType,
            @RequestParam(value = "dimensionId", required = false) String dimensionId,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        MetricSnapshotQuery query = new MetricSnapshotQuery();
        query.setMetricCode(metricCode);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setDimensionType(dimensionType);
        query.setDimensionId(dimensionId);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(analyticsAppService.listSnapshots(query, CrmActorContext.from(request)));
    }
}
