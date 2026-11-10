package com.bluewhale.party.integration.domain.repository;

import com.bluewhale.party.integration.domain.model.EmployeeSnapshot;

import java.util.Optional;

/**
 * 员工快照仓储端口。
 *
 * @author BlueWhale
 */
public interface EmployeeSnapshotRepository {

    /**
     * 按员工编号查快照。
     *
     * @param employeeNo 员工编号
     * @return 快照
     */
    Optional<EmployeeSnapshot> findByEmployeeNo(String employeeNo);

    /**
     * 保存在职快照（按员工编号 upsert）。
     *
     * @param snapshot 快照
     * @return 主键
     */
    Long save(EmployeeSnapshot snapshot);

    /**
     * 在职员工数（覆盖率分母）。
     *
     * @return 在职员工数
     */
    long countActive();
}
