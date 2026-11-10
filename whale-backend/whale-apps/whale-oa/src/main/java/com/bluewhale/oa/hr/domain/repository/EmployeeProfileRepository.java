package com.bluewhale.oa.hr.domain.repository;

import com.bluewhale.oa.hr.domain.model.EmployeeProfile;
import com.bluewhale.oa.hr.domain.model.EmployeeProfileQuery;

import java.util.List;
import java.util.Optional;

/**
 * 员工扩展档案仓储。
 *
 * @author BlueWhale
 */
public interface EmployeeProfileRepository {

    /**
     * 分页查询员工档案。
     *
     * @param query 查询条件
     * @return 当前页档案
     */
    List<EmployeeProfile> page(EmployeeProfileQuery query);

    /**
     * 统计员工档案数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(EmployeeProfileQuery query);

    /**
     * 按主键查询档案。
     *
     * @param id 主键
     * @return 档案
     */
    Optional<EmployeeProfile> findById(Long id);

    /**
     * 按用户ID查询档案。
     *
     * @param userId 用户ID
     * @return 档案
     */
    Optional<EmployeeProfile> findByUserId(Long userId);

    /**
     * 新增档案。
     *
     * @param profile 档案
     */
    void insert(EmployeeProfile profile);

    /**
     * 更新档案。
     *
     * @param profile 档案
     */
    void update(EmployeeProfile profile);

    /**
     * 逻辑删除档案。
     *
     * @param id 主键
     */
    void deleteById(Long id);
}
