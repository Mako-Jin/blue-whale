package com.bluewhale.erp.hr.domain.repository;

import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.model.OrgUnit;

import java.util.List;
import java.util.Optional;

/**
 * 组织单元仓储。
 *
 * @author BlueWhale
 */
public interface HrOrgRepository {

    /**
     * 查询组织列表（不分页，按编码排序）。
     *
     * @param query 查询条件
     * @return 组织列表
     */
    List<OrgUnit> list(HrQuery query);

    /**
     * 按编码查询。
     *
     * @param code 组织编码
     * @return 组织
     */
    Optional<OrgUnit> findByCode(String code);

    /**
     * 新增组织。
     *
     * @param org 组织
     */
    void insert(OrgUnit org);

    /**
     * 更新组织。
     *
     * @param org 组织
     */
    void update(OrgUnit org);
}
