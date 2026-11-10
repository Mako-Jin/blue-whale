package com.bluewhale.party.integration.domain.gateway;

import com.bluewhale.party.integration.domain.model.ErpDirectoryPage;

/**
 * ERP 员工目录只读网关。
 *
 * <p>只暴露「按页读」，不提供回写：快照与源头的方向必须单向。</p>
 *
 * @author BlueWhale
 */
public interface ErpEmployeeGateway {

    /**
     * 拉取一页员工目录。
     *
     * @param current 页码（从 1 起）
     * @param size 每页条数
     * @return 分页结果
     */
    ErpDirectoryPage fetchPage(long current, long size);
}
