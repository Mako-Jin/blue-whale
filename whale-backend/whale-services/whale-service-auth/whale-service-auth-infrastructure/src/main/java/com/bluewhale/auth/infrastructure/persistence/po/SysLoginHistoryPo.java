package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录历史实体（权限中心扩展 M3：对应 {@code sys_login_history}，append-only，
 * 建表即无 update_time 与 deleted 列，禁止 UPDATE/DELETE）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_login_history")
public class SysLoginHistoryPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID */
    private Long tenantId;
    /** 用户 ID（认证失败且用户不存在时为 NULL） */
    private Long userId;
    /** 尝试登录的用户名 */
    private String username;
    /** 登录类型：PASSWORD / SOCIAL / OAUTH2 / SWITCH / REFRESH / LOGOUT */
    private String loginType;
    /** 结果：SUCCESS 成功 / FAILURE 失败 / LOGOUT 登出 */
    private String loginResult;
    /** 失败原因（结果为 FAILURE 时记录） */
    private String failureReason;
    /** 来源 IP */
    private String ip;
    /** User-Agent */
    private String userAgent;
    /** 设备指纹（可为空：账本降级路径无指纹） */
    private String deviceFingerprint;
    /** 事件时间 */
    private LocalDateTime loginTime;
}
