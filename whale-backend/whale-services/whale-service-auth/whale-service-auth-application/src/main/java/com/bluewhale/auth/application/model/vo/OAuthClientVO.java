package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.aggregate.OAuthClientAggregate;
import com.bluewhale.auth.domain.model.vo.GrantType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * OAuth2 客户端视图对象（权限中心扩展 M0）。
 *
 * <p>响应中不含任何密钥信息；密钥明文只在创建/轮换时由
 * {@link OAuthClientSecretVO} 返回一次。</p>
 *
 * @author BlueWhale
 */
@Data
public class OAuthClientVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String clientId;
    private String clientName;
    private List<String> redirectUris;
    private List<String> grantTypes;
    private List<String> scopes;
    private Integer accessTokenTtl;
    private Integer refreshTokenTtl;
    private Boolean autoApprove;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 由客户端聚合转换为视图对象
     *
     * @param client 客户端聚合
     * @return 客户端视图对象
     */
    public static OAuthClientVO from(OAuthClientAggregate client) {
        OAuthClientVO vo = new OAuthClientVO();
        vo.setId(client.getId());
        vo.setClientId(client.getClientId().value());
        vo.setClientName(client.getClientName());
        vo.setRedirectUris(client.getRedirectUris().values());
        vo.setGrantTypes(client.getGrantTypes().stream().map(GrantType::value).toList());
        vo.setScopes(client.getScopes().values());
        vo.setAccessTokenTtl(client.getAccessTokenTtl());
        vo.setRefreshTokenTtl(client.getRefreshTokenTtl());
        vo.setAutoApprove(client.isAutoApprove());
        vo.setStatus(client.getStatus());
        vo.setCreateTime(client.getCreateTime());
        vo.setUpdateTime(client.getUpdateTime());
        return vo;
    }
}
