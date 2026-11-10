package com.bluewhale.pdm.integration.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.integration.service.PdmIntSyncService;
import com.bluewhale.pdm.integration.support.PdmIntRequestContext;
import com.bluewhale.pdm.integration.vo.PdmIntErpPartResultVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ERP 集成接口
 *
 * <p>按零件编码幂等推送物料到 ERP；重复推送只更新既有物料，不重复创建。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pdm/integration/erp")
@RequiredArgsConstructor
public class PdmIntErpController {

    /** 审计模块 */
    private static final String MODULE = "PDM 集成";

    private final PdmIntSyncService syncService;

    /**
     * 推送零件到 ERP
     *
     * @param partId         零件ID
     * @param servletRequest 当前请求
     * @return 同步结果
     */
    @PostMapping("/part/{partId}/sync")
    @RequirePermission("sys:pdm:int:erp:sync")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "推送零件到 ERP",
            targetType = "pdm_product")
    public Result<PdmIntErpPartResultVO> syncPart(@PathVariable Long partId,
                                                  HttpServletRequest servletRequest) {
        return Result.ok(syncService.syncPartToErp(partId,
                PdmIntRequestContext.requireUserId(servletRequest)));
    }
}
