package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.aggregate.BrandConfigAggregate;
import com.bluewhale.auth.infrastructure.persistence.po.SysBrandConfigPo;

/**
 * BrandConfig 聚合与其持久化对象（PO）的转换器（权限中心扩展 M0）。
 *
 * @author BlueWhale
 */
public final class BrandConfigPersistenceAssembler {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private BrandConfigPersistenceAssembler() {
    }

    /**
     * PO → 领域对象
     *
     * @param po 品牌配置持久化对象
     * @return 品牌配置聚合
     */
    public static BrandConfigAggregate toDomain(SysBrandConfigPo po) {
        return BrandConfigAggregate.reconstitute(po.getId(), po.getTargetType(), po.getTargetId(), po.getDomain(),
                po.getBrandName(), po.getLogoUrl(), po.getLoginTitle(), po.getInstitutionName(), po.getCopyright(),
                po.getPrimaryColor(), po.getBackgroundUrl(), po.getPriority(), po.getStatus(),
                po.getCreateTime(), po.getUpdateTime());
    }

    /**
     * 领域对象 → PO：仅转换 BrandConfig 聚合所承载的字段
     *
     * @param brand 品牌配置聚合
     * @return 品牌配置持久化对象
     */
    public static SysBrandConfigPo toPo(BrandConfigAggregate brand) {
        SysBrandConfigPo po = new SysBrandConfigPo();
        po.setId(brand.getId());
        po.setTargetType(brand.getTargetType());
        po.setTargetId(brand.getTargetId());
        po.setDomain(brand.getDomain());
        po.setBrandName(brand.getBrandName());
        po.setLogoUrl(brand.getLogoUrl());
        po.setLoginTitle(brand.getLoginTitle());
        po.setInstitutionName(brand.getInstitutionName());
        po.setCopyright(brand.getCopyright());
        po.setPrimaryColor(brand.getPrimaryColor());
        po.setBackgroundUrl(brand.getBackgroundUrl());
        po.setPriority(brand.getPriority());
        po.setStatus(brand.getStatus());
        po.setCreateTime(brand.getCreateTime());
        po.setUpdateTime(brand.getUpdateTime());
        return po;
    }
}
