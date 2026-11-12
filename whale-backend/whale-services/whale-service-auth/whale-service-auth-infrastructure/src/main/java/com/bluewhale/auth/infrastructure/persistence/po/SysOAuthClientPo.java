package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * OAuth2 客户端实体（权限中心扩展 M0：对应 {@code sys_oauth_client}）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_oauth_client")
public class SysOAuthClientPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 客户端标识：全局唯一 */
    private String clientId;
    /** 客户端密钥：BCrypt 哈希，明文不落库 */
    private String clientSecret;
    private String clientName;
    /** 回调地址白名单：逗号分隔 */
    private String redirectUris;
    /** 授权类型：逗号分隔 */
    private String grantTypes;
    /** 授权范围：逗号分隔 */
    private String scopes;
    private Integer accessTokenTtl;
    private Integer refreshTokenTtl;
    /** 是否免确认授权：1 是 / 0 否 */
    private Integer autoApprove;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
