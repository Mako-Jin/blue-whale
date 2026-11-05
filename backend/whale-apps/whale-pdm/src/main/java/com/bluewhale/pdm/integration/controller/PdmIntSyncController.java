package com.bluewhale.pdm.integration.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.integration.dto.PdmIntSyncQueryRequest;
import com.bluewhale.pdm.integration.service.PdmIntSyncService;
import com.bluewhale.pdm.integration.support.PdmIntRequestContext;
import com.bluewhale.pdm.integration.vo.PdmIntSyncLogVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 集成同步运维接口
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pdm/integration/sync")
@RequiredArgsConstructor
public class PdmIntSyncController {

    /** 审计模块 */
    private static final String MODULE = "PDM 集成";

    private final PdmIntSyncService syncService;

    /**
     * 分页查询同步日志
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pdm:int:sync:list")
    public Result<PageResult<PdmIntSyncLogVO>> page(PdmIntSyncQueryRequest request) {
        return Result.ok(syncService.page(request));
    }

    /**
     * 重试同步
     *
     * @param id             同步日志ID
     * @param servletRequest 当前请求
     * @return 同步日志视图
     */
    @PostMapping("/{id}/retry")
    @RequirePermission("sys:pdm:int:sync:retry")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "重试集成同步",
            targetType = "pdm_int_sync_log")
    public Result<PdmIntSyncLogVO> retry(@PathVariable Long id, HttpServletRequest servletRequest) {
        return Result.ok(syncService.retry(id, PdmIntRequestContext.requireUserId(servletRequest)));
    }
}
