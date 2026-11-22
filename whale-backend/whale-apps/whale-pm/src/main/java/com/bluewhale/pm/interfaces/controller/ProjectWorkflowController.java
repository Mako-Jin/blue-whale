package com.bluewhale.pm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pm.application.service.ProjectWorkflowAppService;
import com.bluewhale.pm.interfaces.dto.StatusSaveRequest;
import com.bluewhale.pm.interfaces.vo.StatusVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作项状态与流转配置接口：按记录 ID 更新或删除。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pm/v{version}", version = "1")
@RequiredArgsConstructor
public class ProjectWorkflowController {

    /** 审计操作对象类型：状态 */
    private static final String TARGET_STATUS = "pm_status";

    /** 审计操作对象类型：流转 */
    private static final String TARGET_TRANSITION = "pm_transition";

    private final ProjectWorkflowAppService workflowAppService;

    /**
     * 更新项目状态（状态编码不可修改）
     *
     * @param id      状态ID
     * @param request 更新请求
     * @return 更新后的状态
     */
    @PutMapping("/status/{id}")
    @RequirePermission("sys:pm:status:edit")
    @AuditLog(operation = "更新项目状态", type = OperateType.UPDATE, targetType = TARGET_STATUS)
    public Result<StatusVO> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusSaveRequest request) {
        return Result.ok(workflowAppService.updateStatus(id, request));
    }

    /**
     * 删除项目状态
     *
     * @param id 状态ID
     * @return 空结果
     */
    @DeleteMapping("/status/{id}")
    @RequirePermission("sys:pm:status:delete")
    @AuditLog(operation = "删除项目状态", type = OperateType.DELETE, targetType = TARGET_STATUS)
    public Result<Void> deleteStatus(@PathVariable Long id) {
        workflowAppService.deleteStatus(id);
        return Result.ok();
    }

    /**
     * 删除状态流转
     *
     * @param id 流转ID
     * @return 空结果
     */
    @DeleteMapping("/transition/{id}")
    @RequirePermission("sys:pm:transition:delete")
    @AuditLog(operation = "删除状态流转", type = OperateType.DELETE, targetType = TARGET_TRANSITION)
    public Result<Void> deleteTransition(@PathVariable Long id) {
        workflowAppService.deleteTransition(id);
        return Result.ok();
    }
}
