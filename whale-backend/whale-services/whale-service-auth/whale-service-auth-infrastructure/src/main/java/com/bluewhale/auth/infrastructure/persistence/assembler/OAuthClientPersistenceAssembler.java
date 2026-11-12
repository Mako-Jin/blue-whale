package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.aggregate.OAuthClientAggregate;
import com.bluewhale.auth.domain.model.vo.GrantType;
import com.bluewhale.auth.infrastructure.persistence.po.SysOAuthClientPo;

import java.util.stream.Collectors;

/**
 * OAuthClient 聚合与其持久化对象（PO）的转换器（权限中心扩展 M0）。
 *
 * @author BlueWhale
 */
public final class OAuthClientPersistenceAssembler {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private OAuthClientPersistenceAssembler() {
    }

    /**
     * PO → 领域对象
     *
     * @param po 客户端持久化对象
     * @return 客户端聚合
     */
    public static OAuthClientAggregate toDomain(SysOAuthClientPo po) {
        return OAuthClientAggregate.reconstitute(po.getId(), po.getClientId(), po.getClientSecret(),
                po.getClientName(), po.getRedirectUris(), po.getGrantTypes(), po.getScopes(),
                po.getAccessTokenTtl(), po.getRefreshTokenTtl(), po.getAutoApprove(), po.getStatus(),
                po.getCreateTime(), po.getUpdateTime());
    }

    /**
     * 领域对象 → PO：仅转换 OAuthClient 聚合所承载的字段
     *
     * @param client 客户端聚合
     * @return 客户端持久化对象
     */
    public static SysOAuthClientPo toPo(OAuthClientAggregate client) {
        SysOAuthClientPo po = new SysOAuthClientPo();
        po.setId(client.getId());
        po.setClientId(client.getClientId().value());
        po.setClientSecret(client.getClientSecretHash());
        po.setClientName(client.getClientName());
        po.setRedirectUris(client.getRedirectUris().asText());
        po.setGrantTypes(client.getGrantTypes().stream().map(GrantType::value).collect(Collectors.joining(",")));
        po.setScopes(client.getScopes().asText());
        po.setAccessTokenTtl(client.getAccessTokenTtl());
        po.setRefreshTokenTtl(client.getRefreshTokenTtl());
        po.setAutoApprove(client.isAutoApprove() ? 1 : 0);
        po.setStatus(client.getStatus());
        po.setCreateTime(client.getCreateTime());
        po.setUpdateTime(client.getUpdateTime());
        return po;
    }
}
