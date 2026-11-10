package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.LoginCommand;
import com.bluewhale.auth.application.model.command.LogoutCommand;
import com.bluewhale.auth.application.model.command.RefreshCommand;
import com.bluewhale.auth.application.model.command.SwitchTenantCommand;
import com.bluewhale.auth.application.model.query.CurrentUserQuery;
import com.bluewhale.auth.application.model.query.TenantListQuery;
import com.bluewhale.auth.application.model.result.CurrentUserResult;
import com.bluewhale.auth.application.model.result.LoginResult;
import com.bluewhale.auth.application.model.result.TenantResult;

import java.util.List;

/**
 * 会话应用服务接口：登录、刷新 Token、切换租户、登出与当前用户信息。
 *
 * <p>实现位于 {@code service.impl.SessionAppServiceImpl}，只依赖应用层端口与领域仓储，
 * 不感知 Spring Security / JWT / MyBatis 等具体实现。</p>
 *
 * @author BlueWhale
 */
public interface SessionAppService {

    /**
     * 登录：按（租户, 用户名, 密码）完成凭据校验并签发一对令牌。
     *
     * @param command 登录命令（租户编码、用户名与明文密码）
     * @return 登录结果（响应体含 accessToken；refreshToken 仅由接口层写入 HttpOnly Cookie）
     */
    LoginResult login(LoginCommand command);

    /**
     * 刷新：refreshToken 换发新的 accessToken + refreshToken（V-03：令牌由 Cookie 携带）。
     * <p>先做签名/过期与令牌类型校验，再查分布式黑名单：命中（已在登出时失效）直接 401，
     * 阻断「登出后仍可用 refreshToken 换发新 token」的提权路径（REQ-SEC-004）。</p>
     * <p>AUTH-39 一次性轮换（{@code whale.auth.refresh.rotation.enabled=true} 时生效）：
     * family 吊销校验 → 登出黑名单校验 → 以 {@code SET NX} 原子抢占「已消费」标记；
     * 抢占失败即判定复用，整族吊销并告警后返回 401；抢占成功则旧 refreshToken 写入登出黑名单、
     * 以同 familyId 签发新一对令牌。关闭时（默认）行为与既有实现一致。</p>
     * <p>用户级撤销（REQ-SEC-014）：停用、删除、改密后写入的撤销时刻同样作用于 refreshToken，
     * 撤销前签发的 refreshToken 不能再换发新令牌，避免绕过 accessToken 撤销继续访问。</p>
     *
     * @param command 刷新命令（Cookie 中解析出的 refreshToken 原文）
     * @return 登录结果（响应体含新 accessToken；新 refreshToken 仅写 Cookie）
     */
    LoginResult refresh(RefreshCommand command);

    /**
     * 当前用户可进入的租户列表（一人多租户切换器数据源）。
     *
     * @param query 我的租户查询
     * @return 启用且当前可用的租户列表，当前令牌租户标记 {@code current=true}
     */
    List<TenantResult> listTenants(TenantListQuery query);

    /**
     * 切换租户：同一 {@code userId} / {@code sub} 只更换令牌中的租户上下文与角色。
     *
     * <p>切换成功后将当前 accessToken 与 Cookie 携带的 refreshToken 写入黑名单（开启轮换时
     * 吊销整个 family），避免新旧租户令牌并存；新令牌使用全新 familyId。</p>
     *
     * @param command 切换租户命令（Authorization 头、目标租户与当前 refreshToken）
     * @return 新租户下的登录结果（新 refreshToken 仅由接口层写 Cookie）
     */
    LoginResult switchTenant(SwitchTenantCommand command);

    /**
     * 登出：将 accessToken 与 refreshToken 一并写入分布式黑名单，使其在剩余有效期内立即失效。
     * <p>TTL 取各自令牌剩余有效期，到期后由 Redis 自动清理；
     * Redis 不可用时降级为客户端丢弃 token，不阻断登出流程。</p>
     *
     * @param command 登出命令（Authorization 头与 refreshToken，均可空）
     */
    void logout(LogoutCommand command);

    /**
     * 当前登录用户信息：角色 / 权限码 / 可见菜单树。
     * <p>权限码与菜单为「动态路由 + 按钮级权限渲染」的数据源：内置超管（super_admin）
     * 授予全量启用权限码与全部启用可见菜单，其余用户按「用户 → 角色 → 权限点」实际授权过滤。</p>
     *
     * @param query 当前登录用户查询
     * @return 当前登录用户的角色、权限码与可见菜单树
     */
    CurrentUserResult me(CurrentUserQuery query);

}
