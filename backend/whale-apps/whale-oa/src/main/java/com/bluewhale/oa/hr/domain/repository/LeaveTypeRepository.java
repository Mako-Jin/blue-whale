package com.bluewhale.oa.hr.domain.repository;

import com.bluewhale.oa.hr.domain.model.LeaveType;
import com.bluewhale.oa.hr.domain.model.LeaveTypeQuery;

import java.util.List;
import java.util.Optional;

/**
 * 假期类型仓储。
 *
 * @author BlueWhale
 */
public interface LeaveTypeRepository {

    /**
     * 分页查询假期类型。
     *
     * @param query 查询条件
     * @return 当前页类型
     */
    List<LeaveType> page(LeaveTypeQuery query);

    /**
     * 统计假期类型数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(LeaveTypeQuery query);

    /**
     * 按主键查询假期类型。
     *
     * @param id 主键
     * @return 假期类型
     */
    Optional<LeaveType> findById(Long id);

    /**
     * 按编码查询假期类型。
     *
     * @param code 编码
     * @return 假期类型
     */
    Optional<LeaveType> findByCode(String code);

    /**
     * 查询全部启用假期类型（按排序号升序）。
     *
     * @return 启用类型
     */
    List<LeaveType> listEnabled();

    /**
     * 新增假期类型。
     *
     * @param leaveType 假期类型
     */
    void insert(LeaveType leaveType);

    /**
     * 更新假期类型。
     *
     * @param leaveType 假期类型
     */
    void update(LeaveType leaveType);
}
