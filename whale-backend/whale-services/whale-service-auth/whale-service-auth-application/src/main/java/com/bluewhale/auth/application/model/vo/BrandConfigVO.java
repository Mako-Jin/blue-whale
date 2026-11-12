package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.aggregate.BrandConfigAggregate;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 品牌配置视图对象（权限中心扩展 M0）。
 *
 * @author BlueWhale
 */
@Data
public class BrandConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String targetType;
    private Long targetId;
    private String domain;
    private String brandName;
    private String logoUrl;
    private String loginTitle;
    private String institutionName;
    private String copyright;
    private String primaryColor;
    private String backgroundUrl;
    private Integer priority;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 由品牌聚合转换为视图对象
     *
     * @param brand 品牌聚合
     * @return 品牌视图对象
     */
    public static BrandConfigVO from(BrandConfigAggregate brand) {
        BrandConfigVO vo = new BrandConfigVO();
        vo.setId(brand.getId());
        vo.setTargetType(brand.getTargetType());
        vo.setTargetId(brand.getTargetId());
        vo.setDomain(brand.getDomain());
        vo.setBrandName(brand.getBrandName());
        vo.setLogoUrl(brand.getLogoUrl());
        vo.setLoginTitle(brand.getLoginTitle());
        vo.setInstitutionName(brand.getInstitutionName());
        vo.setCopyright(brand.getCopyright());
        vo.setPrimaryColor(brand.getPrimaryColor());
        vo.setBackgroundUrl(brand.getBackgroundUrl());
        vo.setPriority(brand.getPriority());
        vo.setStatus(brand.getStatus());
        vo.setCreateTime(brand.getCreateTime());
        vo.setUpdateTime(brand.getUpdateTime());
        return vo;
    }
}
