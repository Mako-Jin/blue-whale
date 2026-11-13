package com.bluewhale.auth.domain.model.entity;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;

/**
 * 登录历史记录实体（对应 {@code sys_login_history}，权限中心扩展 M3）。
 *
 * <p><b>append-only</b>：只允许插入，不承载业务状态机，不可修改历史（无 UPDATE 语义）；
 * 写入失败由账本端口降级为告警，不得阻断登录（ADR #7）。</p>
 *
 * @param id 主键（仓储侧生成，新建时为 {@code null}）
 * @param tenantId 租户 ID
 * @param userId 用户 ID（认证失败且用户不存在时为 {@code null}）
 * @param username 尝试登录的用户名
 * @param loginType 登录类型（PASSWORD / SOCIAL / OAUTH2 / SWITCH / REFRESH / LOGOUT）
 * @param loginResult 结果（SUCCESS / FAILURE / LOGOUT）
 * @param failureReason 失败原因（可空）
 * @param ip 来源 IP（可空）
 * @param userAgent User-Agent（可空）
 * @param deviceFingerprint 设备指纹（可空）
 * @param loginTime 事件时间
 * @author BlueWhale
 */
public record LoginHistoryRecord(Long id, Long tenantId, Long userId, String username, String loginType,
                                 String loginResult, String failureReason, String ip, String userAgent,
                                 String deviceFingerprint, LocalDateTime loginTime) implements Entity<Long> {

    /** 登录类型：密码登录 */
    public static final String TYPE_PASSWORD = "PASSWORD";
    /** 登录类型：三方登录 */
    public static final String TYPE_SOCIAL = "SOCIAL";
    /** 登录类型：切换租户 */
    public static final String TYPE_SWITCH = "SWITCH";
    /** 登录类型：刷新令牌 */
    public static final String TYPE_REFRESH = "REFRESH";
    /** 登录类型：登出 */
    public static final String TYPE_LOGOUT = "LOGOUT";
    /** 结果：成功 */
    public static final String RESULT_SUCCESS = "SUCCESS";
    /** 结果：失败 */
    public static final String RESULT_FAILURE = "FAILURE";
    /** 结果：登出 */
    public static final String RESULT_LOGOUT = "LOGOUT";

    /**
     * 构造并校验记录不变量
     *
     * @throws DomainException 关键字段缺失时抛出
     */
    public LoginHistoryRecord {
        if (tenantId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "登录历史必须归属租户");
        }
        if (username == null || username.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "登录用户名不能为空");
        }
        if (loginType == null || loginType.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "登录类型不能为空");
        }
        if (loginResult == null || loginResult.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "登录结果不能为空");
        }
        if (loginTime == null) {
            loginTime = LocalDateTime.now();
        }
    }

    /**
     * 工厂方法：成功记录
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param username 用户名
     * @param loginType 登录类型
     * @param ip 来源 IP（可空）
     * @param userAgent User-Agent（可空）
     * @param deviceFingerprint 设备指纹（可空）
     * @return 登录历史记录
     */
    public static LoginHistoryRecord success(Long tenantId, Long userId, String username, String loginType,
                                             String ip, String userAgent, String deviceFingerprint) {
        return new LoginHistoryRecord(null, tenantId, userId, username, loginType, RESULT_SUCCESS,
                null, ip, userAgent, deviceFingerprint, LocalDateTime.now());
    }

    /**
     * 工厂方法：失败记录
     *
     * @param tenantId 租户 ID（可空：租户解析失败场景）
     * @param userId 用户 ID（可空：用户不存在场景）
     * @param username 用户名
     * @param loginType 登录类型
     * @param failureReason 失败原因
     * @param ip 来源 IP（可空）
     * @param userAgent User-Agent（可空）
     * @param deviceFingerprint 设备指纹（可空）
     * @return 登录历史记录
     */
    public static LoginHistoryRecord failure(Long tenantId, Long userId, String username, String loginType,
                                             String failureReason, String ip, String userAgent,
                                             String deviceFingerprint) {
        return new LoginHistoryRecord(null, tenantId, userId, username, loginType, RESULT_FAILURE,
                failureReason, ip, userAgent, deviceFingerprint, LocalDateTime.now());
    }

    /**
     * 获取主键
     *
     * @return 主键（仓储侧生成）
     */
    @Override
    public Long getId() {
        return id;
    }
}
