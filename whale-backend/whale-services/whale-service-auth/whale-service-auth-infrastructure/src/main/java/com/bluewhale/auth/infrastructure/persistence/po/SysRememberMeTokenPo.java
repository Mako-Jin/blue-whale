package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 记住我令牌实体（权限中心扩展 M3：对应 {@code sys_remember_me_token}，
 * 建表即无 deleted 列，撤销走 revoked 列）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_remember_me_token")
public class SysRememberMeTokenPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID */
    private Long tenantId;
    /** 用户 ID */
    private Long userId;
    /** 序列号（全局唯一，安全告警定位键） */
    private String series;
    /** 令牌 SHA-256 哈希（不落明文） */
    private String tokenHash;
    /** 关联设备 ID */
    private Long deviceId;
    /** 签发时间 */
    private LocalDateTime issuedAt;
    /** 最近使用时间（每次访问刷新） */
    private LocalDateTime lastUsedAt;
    /** 过期时间 */
    private LocalDateTime expiresAt;
    /** 撤销标记：0 未撤销 / 1 已撤销 */
    private Integer revoked;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
