package com.bluewhale.auth.vo;

import com.bluewhale.auth.domain.tenant.aggregate.Tenant;
import lombok.Data;

import java.io.Serializable;

/**
 * 「我的租户」列表项（Wave 5.54 Phase 5.1：一人多租户切换器数据源）。
 *
 * @author BlueWhale
 */
@Data
public class MyTenantVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    /** 是否为当前令牌所在租户 */
    private boolean current;

    /**
     * 由租户聚合构建列表项
     *
     * @param tenant 租户聚合
     * @param current 是否为当前租户
     * @return 列表项
     */
    public static MyTenantVO from(Tenant tenant, boolean current) {
        MyTenantVO vo = new MyTenantVO();
        vo.setTenantId(tenant.getId());
        vo.setTenantCode(tenant.getTenantCode());
        vo.setTenantName(tenant.getTenantName());
        vo.setCurrent(current);
        return vo;
    }
}
