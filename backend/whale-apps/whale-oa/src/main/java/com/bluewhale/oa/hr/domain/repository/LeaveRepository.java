package com.bluewhale.oa.hr.domain.repository;

import com.bluewhale.oa.hr.domain.model.Leave;
import com.bluewhale.oa.hr.domain.model.LeaveQuery;

import java.util.List;
import java.util.Optional;

/**
 * 请假申请仓储。
 *
 * @author BlueWhale
 */
public interface LeaveRepository {

    /**
     * 分页查询请假申请。
     *
     * @param query 查询条件
     * @return 当前页申请
     */
    List<Leave> page(LeaveQuery query);

    /**
     * 统计请假申请数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(LeaveQuery query);

    /**
     * 按主键查询申请。
     *
     * @param id 主键
     * @return 申请
     */
    Optional<Leave> findById(Long id);

    /**
     * 是否存在同一申请单号。
     *
     * @param code 申请单号
     * @param excludeId 排除的主键（更新时排除自身），新增传 {@code null}
     * @return 存在返回 {@code true}
     */
    boolean existsByCode(String code, Long excludeId);

    /**
     * 新增申请。
     *
     * @param leave 申请
     */
    void insert(Leave leave);

    /**
     * 更新申请。
     *
     * @param leave 申请
     */
    void update(Leave leave);

    /**
     * 逻辑删除申请。
     *
     * @param id 主键
     */
    void deleteById(Long id);
}
