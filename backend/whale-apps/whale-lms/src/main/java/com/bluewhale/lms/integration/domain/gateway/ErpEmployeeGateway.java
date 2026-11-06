package com.bluewhale.lms.integration.domain.gateway;

import com.bluewhale.lms.integration.domain.model.ErpDirectoryPage;

/**
 * ERP 员工目录只读网关。
 *
 * <p>LMS 不持有员工主数据，只按页拉取快照。接口刻意只暴露「按页读」，
 * 不提供回写方法——快照与源头的方向必须是单向的，否则 ERP 与 LMS
 * 会互相覆盖同一条员工信息。</p>
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
