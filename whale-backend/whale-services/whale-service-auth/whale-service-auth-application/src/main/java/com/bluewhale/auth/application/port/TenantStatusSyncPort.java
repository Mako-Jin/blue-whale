package com.bluewhale.auth.application.port;

import com.bluewhale.auth.domain.aggregate.Tenant;

/**
 * 租户状态缓存同步端口：应用层只表达「租户状态已变更」，由基础设施层写入网关共享缓存。
 *
 * @author BlueWhale
 */
public interface TenantStatusSyncPort {

    /**
     * 同步租户启用状态与到期时间（事务内调用时应延迟到提交后写入）
     *
     * @param tenant 租户聚合
     */
    void sync(Tenant tenant);
}
