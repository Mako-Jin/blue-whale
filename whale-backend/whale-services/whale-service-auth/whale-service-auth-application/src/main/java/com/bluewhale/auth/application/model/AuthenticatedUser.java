package com.bluewhale.auth.application.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用层当前用户快照：登录 / 刷新 / 切换租户用例的用户载体，不含口令。
 *
 * @param tenantId 租户 ID
 * @param userId 用户 ID
 * @param username 用户名
 * @param nickname 昵称
 * @param roles 角色 code 列表（不可变拷贝）
 * @param enabled 账号是否启用
 * @param mustChangePassword 是否需强制修改口令（T-03 出厂口令轮换后首登）
 * @param passwordUpdatedTime 最近一次改密时间（REQ-SEC-023：口令到期判定依据，可空）
 * @author BlueWhale
 *
 * TODO 这个是不是算一个DTO，DTO是否需要在新建一个dto包，专门存放DTO类
 */
public record AuthenticatedUser(
    Long tenantId, Long userId, String username,
    String nickname, List<String> roles, boolean enabled,
    boolean mustChangePassword, LocalDateTime passwordUpdatedTime
) {

    /**
     * 紧凑构造器：角色列表做不可变拷贝，{@code null} 视为空列表
     */
    public AuthenticatedUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /**
     * 兼容构造器：不携带改密时间（旧调用方与测试）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param username 用户名
     * @param nickname 昵称
     * @param roles 角色 code 列表
     * @param enabled 账号是否启用
     * @param mustChangePassword 是否强制修改口令
     */
    public AuthenticatedUser(
        Long tenantId, Long userId, String username, String nickname,
        List<String> roles, boolean enabled, boolean mustChangePassword
    ) {
        this(tenantId, userId, username, nickname, roles, enabled, mustChangePassword, null);
    }
}
