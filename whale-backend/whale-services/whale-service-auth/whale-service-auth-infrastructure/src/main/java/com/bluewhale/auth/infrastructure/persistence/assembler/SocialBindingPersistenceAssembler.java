package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.aggregate.SocialBindingAggregate;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserSocialBindingPo;

/**
 * 三方绑定聚合与持久化对象装配器（权限中心扩展 M2）。
 *
 * @author BlueWhale
 */
public final class SocialBindingPersistenceAssembler {

    /**
     * 工具类禁止实例化
     */
    private SocialBindingPersistenceAssembler() {
    }

    /**
     * 聚合根 → 持久化对象
     *
     * @param aggregate 绑定聚合
     * @return 持久化对象
     */
    public static SysUserSocialBindingPo toPo(SocialBindingAggregate aggregate) {
        SysUserSocialBindingPo po = new SysUserSocialBindingPo();
        po.setId(aggregate.getId());
        po.setTenantId(aggregate.getTenantId());
        po.setUserId(aggregate.getUserId());
        po.setProvider(aggregate.getProvider().code());
        po.setProviderUserId(aggregate.getProviderUserId().value());
        po.setUnionId(aggregate.getUnionId());
        po.setNickname(aggregate.getNickname());
        po.setAvatar(aggregate.getAvatar());
        po.setStatus(aggregate.getStatus());
        po.setCreateTime(aggregate.getCreateTime());
        po.setUpdateTime(aggregate.getUpdateTime());
        return po;
    }

    /**
     * 持久化对象 → 聚合根
     *
     * @param po 持久化对象
     * @return 绑定聚合
     */
    public static SocialBindingAggregate toDomain(SysUserSocialBindingPo po) {
        return SocialBindingAggregate.reconstitute(po.getId(), po.getTenantId(), po.getUserId(),
                po.getProvider(), po.getProviderUserId(), po.getUnionId(), po.getNickname(),
                po.getAvatar(), po.getStatus(), po.getCreateTime(), po.getUpdateTime());
    }
}
