package com.bluewhale.party.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.party.common.PartyPermissions;
import com.bluewhale.party.integration.application.service.EmployeeSnapshotAppService;
import com.bluewhale.party.integration.application.view.EmployeeSyncResultView;
import com.bluewhale.party.interfaces.support.PartyActorContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 集成接口（M-P4）。
 *
 * <p>只暴露「拉」：员工目录从 ERP 单向同步到本地快照，本服务不回写 ERP。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/party/v{version}/integration", version = "1")
@RequiredArgsConstructor
public class PartyIntegrationController {

    private final EmployeeSnapshotAppService employeeSnapshotAppService;

    /**
     * 同步 ERP 员工目录（失败保留旧快照）。
     *
     * @param request 当前请求
     * @return 同步结果
     */
    @PostMapping("/employee/sync")
    @RequirePermission(PartyPermissions.METRIC_MANAGE)
    @AuditLog(operation = "同步 ERP 员工目录", type = OperateType.UPDATE, targetType = "pty_employee_snapshot")
    public Result<EmployeeSyncResultView> syncEmployee(HttpServletRequest request) {
        return Result.ok(employeeSnapshotAppService.sync(PartyActorContext.from(request)));
    }
}
