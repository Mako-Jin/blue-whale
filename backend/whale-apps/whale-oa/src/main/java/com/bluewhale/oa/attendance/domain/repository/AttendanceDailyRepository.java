package com.bluewhale.oa.attendance.domain.repository;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.domain.model.AttendanceDaily;
import com.bluewhale.oa.attendance.domain.model.AttendanceDailyQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 考勤日汇总仓储端口。
 *
 * @author BlueWhale
 */
public interface AttendanceDailyRepository {

    /**
     * 按用户与工作日期查询日汇总。
     *
     * @param userId 用户ID
     * @param workDate 工作日期
     * @return 日汇总
     */
    Optional<AttendanceDaily> findByUserAndDate(Long userId, LocalDate workDate);

    /**
     * 保存日汇总。
     *
     * @param daily 日汇总
     */
    void save(AttendanceDaily daily);

    /**
     * 分页查询日汇总。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AttendanceDaily> page(AttendanceDailyQuery query);

    /**
     * 按用户与日期区间查询日汇总。
     *
     * @param userId 用户ID
     * @param dateFrom 起始日期
     * @param dateTo 结束日期
     * @return 日汇总列表
     */
    List<AttendanceDaily> listByUser(Long userId, LocalDate dateFrom, LocalDate dateTo);
}
