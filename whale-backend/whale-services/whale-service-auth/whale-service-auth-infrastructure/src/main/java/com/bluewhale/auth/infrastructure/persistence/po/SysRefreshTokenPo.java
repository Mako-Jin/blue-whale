package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 刷新令牌实体（权限中心扩展 M3：对应 {@code sys_refresh_token}，
 * 建表即无 deleted 列，撤销走 revoked/revoked_at/revoked_reason 三列）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_refresh_token")
public class SysRefreshTokenPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID */
    private Long tenantId;
    /** 用户 ID */
    private Long userId;
    /** JWT ID（全局唯一） */
    private String jti;
    /** 令牌族 ID（检测到重放时整族撤销） */
    private String familyId;
    /** 签发时间 */
    private LocalDateTime issuedAt;
    /** 过期时间 */
    private LocalDateTime expiresAt;
    /** 撤销标记：0 未撤销 / 1 已撤销 */
    private Integer revoked;
    /** 撤销时间 */
    private LocalDateTime revokedAt;
    /** 撤销原因 */
    private String revokedReason;
    /** 关联设备 ID */
    private Long deviceId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
