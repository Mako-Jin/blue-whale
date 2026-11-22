package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mall.common.MallPermissions;
import com.bluewhale.mall.interfaces.support.MallActorContext;
import com.bluewhale.mall.order.application.service.MallReportAppService;
import com.bluewhale.mall.order.application.view.DailyReportView;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 商城运营报表接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mall/v{version}/report", version = "1")
@RequiredArgsConstructor
public class MallReportController {

    /** 报表应用服务 */
    private final MallReportAppService mallReportAppService;

    /**
     * 运营日报表（按日聚合订单头，不建快照表）。
     *
     * <p>from 缺省为 7 天前，to 缺省为当天；from 晚于 to 时返回空列表。</p>
     *
     * @param from    起始日期（含，可空）
     * @param to      截止日期（含，可空）
     * @param request 当前请求
     * @return 按日统计列表
     */
    @GetMapping("/daily")
    @RequirePermission(MallPermissions.ORDER_LIST)
    public Result<List<DailyReportView>> daily(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        return Result.ok(mallReportAppService.daily(from, to, MallActorContext.from(request)));
    }
}
