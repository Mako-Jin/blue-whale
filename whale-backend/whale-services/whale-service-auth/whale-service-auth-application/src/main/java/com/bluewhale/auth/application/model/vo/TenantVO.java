package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.aggregate.Tenant;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户视图对象（Wave 5.54 Phase 1）。
 *
 * @author BlueWhale
 */
@Data
public class TenantVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String tenantCode;
    private String tenantName;
    /** PLATFORM 平台运营 / TENANT 业务租户 */
    private String tenantType;
    /** 1 启用 / 0 停用 */
    private Integer status;
    private LocalDateTime expireTime;
    private String quotaJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 由租户聚合转换为视图对象
     *
     * @param tenant 租户聚合
     * @return 租户视图对象
     */
    public static TenantVO from(Tenant tenant) {
        TenantVO vo = new TenantVO();
        vo.setId(tenant.getId());
        vo.setTenantCode(tenant.getTenantCode());
        vo.setTenantName(tenant.getTenantName());
        vo.setTenantType(tenant.getTenantType());
        vo.setStatus(tenant.getStatus());
        vo.setExpireTime(tenant.getExpireTime());
        vo.setQuotaJson(tenant.getQuotaJson());
        vo.setCreateTime(tenant.getCreateTime());
        vo.setUpdateTime(tenant.getUpdateTime());
        return vo;
    }
}
