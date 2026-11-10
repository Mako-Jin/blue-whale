package com.bluewhale.erp.mfg.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.mfg.application.service.CrpAppService;
import com.bluewhale.erp.mfg.application.view.CrpView;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * CRP 产能负荷与排程接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/mfg/crp")
@RequiredArgsConstructor
public class CrpController {

    private final CrpAppService crpAppService;

    /**
     * 查询产能负荷与工序排程。
     *
     * @param fromDate       开始日期
     * @param toDate         结束日期
     * @param workCenterCode 工作中心筛选
     * @return CRP 结果
     */
    @GetMapping("/load")
    @RequirePermission("sys:erp:mfg:crp:view")
    public Result<CrpView> load(
            @RequestParam(value = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "workCenterCode", required = false) String workCenterCode) {
        return Result.ok(crpAppService.load(fromDate, toDate, workCenterCode));
    }
}
