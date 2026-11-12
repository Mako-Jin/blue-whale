package com.bluewhale.auth.application.model;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 应用层当前用户快照：登录 / 刷新 / 切换租户用例的用户载体，不含口令。
 *
 * <p>应用层内部模型（读快照 / 值对象），仅在各用例内部流转后转换成 result，
 * 不跨协议边界——与 interfaces 层的 request/response DTO 是两类对象。</p>
 *
 * @param tokenTenantId 令牌签发租户 ID（当前会话令牌所属租户；切换租户后由新令牌携带新值，
 *                      与「用户归属租户」是两个口径，需要后者时经聚合查询）
 * @param userId 用户 ID
 * @param username 用户名
 * @param nickname 昵称
 * @param roles 角色 code 集合（不可变拷贝；角色编码天然去重、无序，故为 Set）
 * @param enabled 账号是否启用
 * @param mustChangePassword 是否需强制修改口令（T-03 出厂口令轮换后首登）
 * @param passwordUpdatedTime 最近一次改密时间（REQ-SEC-023：口令到期判定依据，可空）
 * @author BlueWhale
 */
public record AuthenticatedUser(
    Long tokenTenantId, Long userId, String username,
    String nickname, Set<String> roles, boolean enabled,
    boolean mustChangePassword, LocalDateTime passwordUpdatedTime
) {

    /**
     * 紧凑构造器：角色集合做不可变拷贝，{@code null} 视为空集合
     */
    public AuthenticatedUser {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /**
     * 兼容构造器：不携带改密时间（旧调用方与测试）
     *
     * @param tokenTenantId 令牌签发租户 ID
     * @param userId 用户 ID
     * @param username 用户名
     * @param nickname 昵称
     * @param roles 角色 code 集合
     * @param enabled 账号是否启用
     * @param mustChangePassword 是否强制修改口令
     */
    public AuthenticatedUser(
        Long tokenTenantId, Long userId, String username, String nickname,
        Set<String> roles, boolean enabled, boolean mustChangePassword
    ) {
        this(tokenTenantId, userId, username, nickname, roles, enabled, mustChangePassword, null);
    }
}
