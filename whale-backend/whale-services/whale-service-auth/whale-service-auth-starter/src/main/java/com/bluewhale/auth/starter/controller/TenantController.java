package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.tenant.TenantAppService;
import com.bluewhale.auth.application.tenant.TenantMembershipAppService;
import com.bluewhale.auth.application.tenant.TenantProvisioningAppService;
import com.bluewhale.auth.application.tenant.command.ChangeTenantStatusCommand;
import com.bluewhale.auth.application.tenant.command.CreateTenantCommand;
import com.bluewhale.auth.application.tenant.command.RenewTenantCommand;
import com.bluewhale.auth.application.tenant.command.UpdateTenantCommand;
import com.bluewhale.auth.application.tenant.query.TenantPageQuery;
import com.bluewhale.auth.application.user.password.TenantPasswordPolicyService;
import com.bluewhale.auth.application.user.password.UpdateTenantPasswordPolicyCommand;
import com.bluewhale.auth.interfaces.api.TenantApi;
import com.bluewhale.auth.interfaces.dto.AssignRolesRequest;
import com.bluewhale.auth.interfaces.dto.TenantCreateRequest;
import com.bluewhale.auth.interfaces.dto.TenantPasswordPolicyRequest;
import com.bluewhale.auth.interfaces.dto.TenantRenewRequest;
import com.bluewhale.auth.interfaces.dto.TenantStatusRequest;
import com.bluewhale.auth.interfaces.dto.TenantUpdateRequest;
import com.bluewhale.auth.interfaces.dto.response.TenantAdminResponse;
import com.bluewhale.auth.interfaces.dto.response.TenantMemberResponse;
import com.bluewhale.auth.interfaces.dto.response.TenantPasswordPolicyResponse;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.sdk.annotation.RequireReauth;
import com.bluewhale.auth.starter.assemble.TenantAssembler;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.idempotent.annotation.Idempotent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户接口实现（Web 适配层）：协议适配 + DTO ↔ Command 转换，业务编排在 application 层。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class TenantController implements TenantApi {

    private final TenantAppService tenantAppService;
    private final TenantProvisioningAppService tenantProvisioningAppService;
    private final TenantMembershipAppService tenantMembershipAppService;
    private final TenantPasswordPolicyService tenantPasswordPolicyService;
    private final TenantAssembler tenantAssembler;

    /**
     * 内网查询可用租户 ID（启用且未过期），供档案/知识库等后台任务按租户执行
     *
     * @return 可用租户 ID 列表
     */
    @Override
    public Result<List<Long>> enabledIds() {
        return Result.ok(tenantAppService.listEnabledTenantIds());
    }

    /**
     * 查询当前租户成员（Wave 5.54 Phase 5.3）：启用关系成员 + 本租户角色
     *
     * @return 成员列表
     */
    @Override
    @RequirePermission("sys:tenant:member:list")
    public Result<List<TenantMemberResponse>> members() {
        return Result.ok(tenantAssembler.toMemberResponses(tenantMembershipAppService.listMembers()));
    }

    /**
     * 全量覆盖成员在当前租户内的角色：授权变更属敏感操作，必留痕
     *
     * @param userId 用户 ID
     * @param request 角色分配请求
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:tenant:member:assign")
    @RequireReauth
    @AuditLog(operation = "分配租户成员角色", type = OperateType.GRANT, targetType = "sys_user",
            targetId = "#userId")
    public Result<Void> assignMemberRoles(@PathVariable Long userId,
                                          @Valid @RequestBody AssignRolesRequest request) {
        tenantMembershipAppService.assignMemberRoles(userId, request.getRoleIds());
        return Result.ok();
    }

    /**
     * 分页查询租户
     *
     * @param pageQuery 分页参数
     * @param keyword 租户编码/名称模糊匹配关键字（可空）
     * @param status 状态精确过滤（可空）
     * @return 租户分页结果
     */
    @Override
    @RequirePermission("sys:tenant:list")
    public Result<PageResult<TenantAdminResponse>> page(PageQuery pageQuery,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Integer status) {
        return Result.ok(tenantAssembler.toPage(tenantAppService.page(
                new TenantPageQuery(pageQuery.getCurrent(), pageQuery.getSize(), keyword, status))));
    }

    /**
     * 租户详情
     *
     * @param id 租户 ID
     * @return 租户响应
     */
    @Override
    @RequirePermission("sys:tenant:list")
    public Result<TenantAdminResponse> detail(@PathVariable Long id) {
        return Result.ok(tenantAssembler.toResponse(tenantAppService.getDetail(id)));
    }

    /**
     * 开通租户：创建租户并初始化管理员、默认角色、菜单与部门根
     *
     * @param request 开通请求
     * @return 新租户响应
     */
    @Override
    @RequirePermission("sys:tenant:add")
    @AuditLog(operation = "开通租户", type = OperateType.CREATE, targetType = "sys_tenant",
            recordParams = false)
    @Idempotent(biz = "auth:tenant:create", key = "#request.tenantCode")
    public Result<TenantAdminResponse> create(@Valid @RequestBody TenantCreateRequest request) {
        return Result.ok(tenantAssembler.toResponse(tenantProvisioningAppService.provision(
                new CreateTenantCommand(request.getTenantCode(), request.getTenantName(),
                        request.getExpireTime(), request.getQuotaJson(), request.getAdminUsername(),
                        request.getAdminPassword(), request.getAdminNickname()))));
    }

    /**
     * 更新租户名称与配额
     *
     * @param id 租户 ID
     * @param request 更新请求
     * @return 更新后的租户响应
     */
    @Override
    @RequirePermission("sys:tenant:edit")
    @AuditLog(operation = "更新租户", type = OperateType.UPDATE, targetType = "sys_tenant", targetId = "#id")
    public Result<TenantAdminResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody TenantUpdateRequest request) {
        return Result.ok(tenantAssembler.toResponse(tenantAppService.update(id,
                new UpdateTenantCommand(request.getTenantName(), request.getQuotaJson()))));
    }

    /**
     * 启用/停用租户
     *
     * @param id 租户 ID
     * @param request 启停请求
     * @return 变更后的租户响应
     */
    @Override
    @RequirePermission("sys:tenant:status")
    @RequireReauth
    @AuditLog(operation = "启停租户", type = OperateType.UPDATE, targetType = "sys_tenant", targetId = "#id")
    public Result<TenantAdminResponse> changeStatus(@PathVariable Long id,
                                                    @Valid @RequestBody TenantStatusRequest request) {
        return Result.ok(tenantAssembler.toResponse(tenantAppService.changeStatus(id,
                new ChangeTenantStatusCommand(request.getStatus()))));
    }

    /**
     * 租户续期
     *
     * @param id 租户 ID
     * @param request 续期请求
     * @return 续期后的租户响应
     */
    @Override
    @RequirePermission("sys:tenant:renew")
    @AuditLog(operation = "租户续期", type = OperateType.UPDATE, targetType = "sys_tenant", targetId = "#id")
    public Result<TenantAdminResponse> renew(@PathVariable Long id,
                                             @Valid @RequestBody TenantRenewRequest request) {
        return Result.ok(tenantAssembler.toResponse(
                tenantAppService.renew(id, new RenewTenantCommand(request.getExpireTime()))));
    }

    /**
     * 查询本租户口令策略（REQ-SEC-023）
     *
     * @return 当前租户生效口令策略
     */
    @Override
    @RequirePermission("sys:tenant:pwdpolicy:list")
    public Result<TenantPasswordPolicyResponse> passwordPolicy() {
        return Result.ok(tenantAssembler.toPasswordPolicyResponse(
                tenantPasswordPolicyService.current()));
    }

    /**
     * 配置本租户口令策略（REQ-SEC-023）
     *
     * @param request 口令策略配置请求
     * @return 更新后的生效口令策略
     */
    @Override
    @RequirePermission("sys:tenant:pwdpolicy:edit")
    @AuditLog(operation = "配置租户口令策略", type = OperateType.UPDATE,
            targetType = "sys_tenant_password_policy")
    public Result<TenantPasswordPolicyResponse> updatePasswordPolicy(
            @Valid @RequestBody TenantPasswordPolicyRequest request) {
        return Result.ok(tenantAssembler.toPasswordPolicyResponse(tenantPasswordPolicyService.update(
                new UpdateTenantPasswordPolicyCommand(request.getMaxAgeDays(), request.getWarnDays()))));
    }
}
