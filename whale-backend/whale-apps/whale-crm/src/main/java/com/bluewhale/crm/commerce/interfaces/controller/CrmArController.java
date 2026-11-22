package com.bluewhale.crm.commerce.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.commerce.application.service.ArReconcileAppService;
import com.bluewhale.crm.commerce.application.view.ArSnapshotView;
import com.bluewhale.crm.commerce.application.view.ArSyncResultView;
import com.bluewhale.crm.commerce.domain.model.ArSnapshotQuery;
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

/**
 * 客户应收对账接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/crm/v{version}/ar", version = "1")
@RequiredArgsConstructor
public class CrmArController {

    private final ArReconcileAppService arReconcileAppService;

    /**
     * 分页查询应收快照。
     *
     * @param keyword 关键字
     * @param customerId 客户 ID
     * @param snapshotDate 快照日期
     * @param current 当前页
     * @param pageNum 当前页（兼容参数）
     * @param size 每页条数
     * @param pageSize 每页条数（兼容参数）
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.AR_RECONCILE)
    public Result<PageResult<ArSnapshotView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "snapshotDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate snapshotDate,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        ArSnapshotQuery query = new ArSnapshotQuery();
        query.setKeyword(keyword);
        query.setCustomerId(customerId);
        query.setSnapshotDate(snapshotDate);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(arReconcileAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 手动同步当日应收快照。
     *
     * @param request 当前请求
     * @return 同步结果
     */
    @PostMapping("/sync")
    @RequirePermission(CrmPermissions.AR_RECONCILE)
    @AuditLog(operation = "同步客户应收对账", type = OperateType.OTHER, targetType = "crm_ar_snapshot")
    public Result<ArSyncResultView> sync(HttpServletRequest request) {
        return Result.ok(arReconcileAppService.sync(CrmActorContext.from(request)));
    }
}
