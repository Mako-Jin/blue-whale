package com.bluewhale.auth.application.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员重置口令结果（REQ-SEC-023）。
 *
 * <p>系统生成一次性随机临时口令，仅在本响应中返回一次供管理员转交用户；
 * 服务端只保存其 BCrypt 密文，且目标用户下次登录被强制改密。</p>
 *
 * @author BlueWhale
 */
@Data
public class ResetPasswordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 一次性临时口令（不落库、不记审计参数，仅展示一次） */
    private String temporaryPassword;

    /** 是否已置强制首登改密 */
    private boolean mustChangePassword;

    /**
     * 构造重置结果
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param temporaryPassword 一次性临时口令
     */
    public ResetPasswordVO(Long userId, String username, String temporaryPassword) {
        this.userId = userId;
        this.username = username;
        this.temporaryPassword = temporaryPassword;
        this.mustChangePassword = true;
    }
}
