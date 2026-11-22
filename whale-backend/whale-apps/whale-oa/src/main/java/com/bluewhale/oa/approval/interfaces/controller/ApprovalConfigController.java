package com.bluewhale.oa.approval.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.approval.application.command.ApprovalConfigSaveCommand;
import com.bluewhale.oa.approval.application.service.ApprovalConfigAppService;
import com.bluewhale.oa.approval.application.view.ApprovalConfigView;
import com.bluewhale.oa.approval.domain.model.ApprovalConfigQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OA 审批业务配置接口。
 *
 * <p>仅管理员使用，供业务类型绑定 Workflow 流程定义、标题模板与默认抄送人。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/approval/config", version = "1")
@RequiredArgsConstructor
public class ApprovalConfigController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "oa_approval_config";

    private final ApprovalConfigAppService approvalConfigAppService;

    /**
     * 分页查询审批配置。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:approval:config:list")
    public Result<PageResult<ApprovalConfigView>> page(ApprovalConfigQuery query) {
        return Result.ok(approvalConfigAppService.page(query));
    }

    /**
     * 查询审批配置详情。
     *
     * @param id 主键ID
     * @return 配置详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:approval:config:list")
    public Result<ApprovalConfigView> detail(@PathVariable Long id) {
        return Result.ok(approvalConfigAppService.detail(id));
    }

    /**
     * 新增审批配置。
     *
     * @param command 保存命令
     * @return 新增后的配置
     */
    @PostMapping
    @RequirePermission("sys:oa:approval:config:edit")
    @AuditLog(operation = "新增审批配置", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<ApprovalConfigView> create(@Valid @RequestBody ApprovalConfigSaveCommand command) {
        return Result.ok(approvalConfigAppService.create(command));
    }

    /**
     * 更新审批配置。
     *
     * @param id 主键ID
     * @param command 保存命令
     * @return 更新后的配置
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:approval:config:edit")
    @AuditLog(operation = "更新审批配置", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ApprovalConfigView> update(@PathVariable Long id,
                                             @Valid @RequestBody ApprovalConfigSaveCommand command) {
        return Result.ok(approvalConfigAppService.update(id, command));
    }

    /**
     * 启用 / 停用审批配置。
     *
     * @param id 主键ID
     * @param enabled 目标状态
     * @return 更新后的配置
     */
    @PutMapping("/{id}/enabled")
    @RequirePermission("sys:oa:approval:config:edit")
    @AuditLog(operation = "启停审批配置", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ApprovalConfigView> changeEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return Result.ok(approvalConfigAppService.changeEnabled(id, enabled));
    }
}
