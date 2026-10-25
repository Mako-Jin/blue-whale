package com.bluewhale.auth.controller;

import com.bluewhale.auth.application.tenant.TenantAppService;
import com.bluewhale.auth.application.tenant.TenantProvisioningAppService;
import com.bluewhale.auth.application.tenant.command.ChangeTenantStatusCommand;
import com.bluewhale.auth.application.tenant.command.CreateTenantCommand;
import com.bluewhale.auth.application.tenant.command.RenewTenantCommand;
import com.bluewhale.auth.application.tenant.command.UpdateTenantCommand;
import com.bluewhale.auth.application.tenant.query.TenantPageQuery;
import com.bluewhale.auth.dto.TenantCreateRequest;
import com.bluewhale.auth.dto.TenantRenewRequest;
import com.bluewhale.auth.dto.TenantStatusRequest;
import com.bluewhale.auth.dto.TenantUpdateRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.vo.TenantVO;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.idempotent.annotation.Idempotent;
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

import java.util.List;

/**
 * 租户管理（Wave 5.54 Phase 1）：平台运营租户开通、查询、启停与续期。
 *
 * <p>所有接口均要求 {@code sys:tenant:*} 权限码，且应用层强制校验当前租户类型为 PLATFORM，
 * 业务租户即使持有权限码也无法访问。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantAppService tenantAppService;
    private final TenantProvisioningAppService tenantProvisioningAppService;

    /**
     * 内网查询可用租户 ID（启用且未过期），供档案/知识库等后台任务按租户执行
     *
     * @return 可用租户 ID 列表
     */
    @GetMapping("/internal/enabled-ids")
    public Result<List<Long>> enabledIds() {
        return Result.ok(tenantAppService.listEnabledTenantIds());
    }

    /**
     * 分页查询租户
     *
     * @param pageQuery 分页参数
     * @param keyword 租户编码/名称模糊匹配关键字（可空）
     * @param status 状态精确过滤（可空）
     * @return 租户分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:tenant:list")
    public Result<PageResult<TenantVO>> page(PageQuery pageQuery,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status) {
        return Result.ok(tenantAppService.page(
                new TenantPageQuery(pageQuery.getCurrent(), pageQuery.getSize(), keyword, status)));
    }

    /**
     * 租户详情
     *
     * @param id 租户 ID
     * @return 租户视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:tenant:list")
    public Result<TenantVO> detail(@PathVariable Long id) {
        return Result.ok(tenantAppService.getDetail(id));
    }

    /**
     * 开通租户：创建租户并初始化管理员、默认角色、菜单与部门根
     *
     * @param request 开通请求
     * @return 新租户视图
     */
    @PostMapping
    @RequirePermission("sys:tenant:add")
    @AuditLog(operation = "开通租户", type = OperateType.CREATE, targetType = "sys_tenant",
            recordParams = false)
    @Idempotent(biz = "auth:tenant:create", key = "#request.tenantCode")
    public Result<TenantVO> create(@Valid @RequestBody TenantCreateRequest request) {
        return Result.ok(tenantProvisioningAppService.provision(new CreateTenantCommand(
                request.getTenantCode(), request.getTenantName(), request.getExpireTime(),
                request.getQuotaJson(), request.getAdminUsername(), request.getAdminPassword(),
                request.getAdminNickname())));
    }

    /**
     * 更新租户名称与配额
     *
     * @param id 租户 ID
     * @param request 更新请求
     * @return 更新后的租户视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:tenant:edit")
    @AuditLog(operation = "更新租户", type = OperateType.UPDATE, targetType = "sys_tenant", targetId = "#id")
    public Result<TenantVO> update(@PathVariable Long id, @Valid @RequestBody TenantUpdateRequest request) {
        return Result.ok(tenantAppService.update(id,
                new UpdateTenantCommand(request.getTenantName(), request.getQuotaJson())));
    }

    /**
     * 启用/停用租户
     *
     * @param id 租户 ID
     * @param request 启停请求
     * @return 变更后的租户视图
     */
    @PutMapping("/{id}/status")
    @RequirePermission("sys:tenant:status")
    @AuditLog(operation = "启停租户", type = OperateType.UPDATE, targetType = "sys_tenant", targetId = "#id")
    public Result<TenantVO> changeStatus(@PathVariable Long id,
                                         @Valid @RequestBody TenantStatusRequest request) {
        return Result.ok(tenantAppService.changeStatus(id,
                new ChangeTenantStatusCommand(request.getStatus())));
    }

    /**
     * 租户续期
     *
     * @param id 租户 ID
     * @param request 续期请求
     * @return 续期后的租户视图
     */
    @PutMapping("/{id}/renew")
    @RequirePermission("sys:tenant:renew")
    @AuditLog(operation = "租户续期", type = OperateType.UPDATE, targetType = "sys_tenant", targetId = "#id")
    public Result<TenantVO> renew(@PathVariable Long id, @Valid @RequestBody TenantRenewRequest request) {
        return Result.ok(tenantAppService.renew(id, new RenewTenantCommand(request.getExpireTime())));
    }
}
