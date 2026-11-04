package com.bluewhale.erp.hr.domain.repository;

import com.bluewhale.erp.hr.domain.model.AttendanceSummary;
import com.bluewhale.erp.hr.domain.model.HrQuery;

import java.util.List;
import java.util.Optional;

/**
 * 考勤汇总仓储。
 *
 * @author BlueWhale
 */
public interface AttendanceRepository {

    /**
     * 分页查询考勤汇总。
     *
     * @param query 查询条件
     * @return 考勤列表
     */
    List<AttendanceSummary> page(HrQuery query);

    /**
     * 统计考勤汇总数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(HrQuery query);

    /**
     * 按员工与期间查询。
     *
     * @param employeeNo 员工编号
     * @param period 期间
     * @return 考勤
     */
    Optional<AttendanceSummary> find(String employeeNo, String period);

    /**
     * 查询期间内全部考勤。
     *
     * @param period 期间
     * @return 考勤列表
     */
    List<AttendanceSummary> listByPeriod(String period);

    /**
     * 新增考勤汇总。
     *
     * @param attendance 考勤
     */
    void insert(AttendanceSummary attendance);

    /**
     * 更新考勤汇总。
     *
     * @param attendance 考勤
     */
    void update(AttendanceSummary attendance);
}
