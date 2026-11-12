package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.AssignRolesRequest;
import com.bluewhale.auth.interfaces.dto.TenantCreateRequest;
import com.bluewhale.auth.interfaces.dto.TenantPasswordPolicyRequest;
import com.bluewhale.auth.interfaces.dto.TenantRenewRequest;
import com.bluewhale.auth.interfaces.dto.TenantStatusRequest;
import com.bluewhale.auth.interfaces.dto.TenantUpdateRequest;
import com.bluewhale.auth.interfaces.dto.response.TenantAdminResponse;
import com.bluewhale.auth.interfaces.dto.response.TenantMemberResponse;
import com.bluewhale.auth.interfaces.dto.response.TenantPasswordPolicyResponse;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 租户 API 契约（Wave 5.54）：平台运营租户开通、查询、启停、续期、成员与口令策略。
 *
 * <p>所有管理接口均要求 {@code sys:tenant:*} 权限码，且应用层强制校验当前租户类型为
 * PLATFORM；内网 {@code /internal/enabled-ids} 仅限服务间调用。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/tenant/v{version}", version = "1")
public interface TenantApi {

    /**
     * 内网查询可用租户 ID（启用且未过期）
     *
     * @return 可用租户 ID 列表
     */
    @GetMapping("/internal/enabled-ids")
    Result<List<Long>> enabledIds();

    /**
     * 查询当前租户成员
     *
     * @return 成员列表
     */
    @GetMapping("/members")
    Result<List<TenantMemberResponse>> members();

    /**
     * 全量覆盖成员在当前租户内的角色
     *
     * @param userId 用户 ID
     * @param request 角色分配请求
     * @return 操作结果
     */
    @PutMapping("/members/{userId}/roles")
    Result<Void> assignMemberRoles(@PathVariable Long userId,
                                   @Valid @RequestBody AssignRolesRequest request);

    /**
     * 分页查询租户
     *
     * @param pageQuery 分页参数
     * @param keyword 租户编码/名称模糊匹配关键字（可空）
     * @param status 状态精确过滤（可空）
     * @return 租户分页结果
     */
    @GetMapping("/page")
    Result<PageResult<TenantAdminResponse>> page(PageQuery pageQuery,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer status);

    /**
     * 租户详情
     *
     * @param id 租户 ID
     * @return 租户响应
     */
    @GetMapping("/{id}")
    Result<TenantAdminResponse> detail(@PathVariable Long id);

    /**
     * 开通租户：创建租户并初始化管理员、默认角色、菜单与部门根
     *
     * @param request 开通请求
     * @return 新租户响应
     */
    @PostMapping
    Result<TenantAdminResponse> create(@Valid @RequestBody TenantCreateRequest request);

    /**
     * 更新租户名称与配额
     *
     * @param id 租户 ID
     * @param request 更新请求
     * @return 更新后的租户响应
     */
    @PutMapping("/{id}")
    Result<TenantAdminResponse> update(@PathVariable Long id,
                                       @Valid @RequestBody TenantUpdateRequest request);

    /**
     * 启用/停用租户
     *
     * @param id 租户 ID
     * @param request 启停请求
     * @return 变更后的租户响应
     */
    @PutMapping("/{id}/status")
    Result<TenantAdminResponse> changeStatus(@PathVariable Long id,
                                             @Valid @RequestBody TenantStatusRequest request);

    /**
     * 租户续期
     *
     * @param id 租户 ID
     * @param request 续期请求
     * @return 续期后的租户响应
     */
    @PutMapping("/{id}/renew")
    Result<TenantAdminResponse> renew(@PathVariable Long id,
                                      @Valid @RequestBody TenantRenewRequest request);

    /**
     * 查询本租户生效口令策略
     *
     * @return 当前租户生效口令策略
     */
    @GetMapping("/password-policy")
    Result<TenantPasswordPolicyResponse> passwordPolicy();

    /**
     * 配置本租户口令策略
     *
     * @param request 口令策略配置请求
     * @return 更新后的生效口令策略
     */
    @PutMapping("/password-policy")
    Result<TenantPasswordPolicyResponse> updatePasswordPolicy(
            @Valid @RequestBody TenantPasswordPolicyRequest request);
}
