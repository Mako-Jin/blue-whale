package com.bluewhale.sts.auth.authentication;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 刷新令牌实体，对应表 {@code bw_auth_refresh_token}。
 *
 * <p><b>刻意不继承 {@code BaseEntity}</b>：令牌只有"签发 / 撤销"两种状态，
 * 没有"更新人"，也不该被逻辑删除 —— 撤销是状态变更，不是删除。
 * 硬塞一个审计基类，只会让表上多出几个永远落不了值的列（与
 * {@code ClientLogEntity} 不继承基类是同一个判断）。
 *
 * <p><b>只存令牌摘要 {@code tokenHash}，不存令牌本身。</b>
 * 库被读取时，攻击者拿到的是无法直接使用的哈希；校验时用摘要比对。
 *
 * <p>{@code jti} 是 JWT 的 ID 声明，唯一索引 —— 支撑两件事：
 * 单条撤销，以及**令牌轮换时的重放检测**（同一个 jti 被用第二次即视为泄露）。
 */
@Getter
@Setter
@TableName("bw_auth_refresh_token")
public class RefreshTokenEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    /** 令牌唯一标识（JWT ID） */
    private String jti;

    /** 令牌摘要：只存哈希 */
    private String tokenHash;

    private Long issuedAt;

    private Long expiresAt;

    /** 是否已撤销：0 否 1 是 */
    private Integer revoked;

    private Long revokedAt;

    private String clientIp;

    private String userAgent;

}
