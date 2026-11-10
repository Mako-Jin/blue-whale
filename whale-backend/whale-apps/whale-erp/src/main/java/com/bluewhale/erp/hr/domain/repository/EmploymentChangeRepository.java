package com.bluewhale.erp.hr.domain.repository;

import com.bluewhale.erp.hr.domain.model.EmploymentChange;
import com.bluewhale.erp.hr.domain.model.HrQuery;

import java.util.List;
import java.util.Optional;

/**
 * 员工异动仓储。
 *
 * @author BlueWhale
 */
public interface EmploymentChangeRepository {

    /**
     * 分页查询异动单。
     *
     * @param query 查询条件
     * @return 异动单列表
     */
    List<EmploymentChange> page(HrQuery query);

    /**
     * 统计异动单数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(HrQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 异动单
     */
    Optional<EmploymentChange> findById(Long id);

    /**
     * 生成异动单号。
     *
     * @return 异动单号
     */
    String nextChangeNo();

    /**
     * 新增异动单。
     *
     * @param change 异动单
     */
    void insert(EmploymentChange change);

    /**
     * 更新异动单。
     *
     * @param change 异动单
     */
    void update(EmploymentChange change);
}
