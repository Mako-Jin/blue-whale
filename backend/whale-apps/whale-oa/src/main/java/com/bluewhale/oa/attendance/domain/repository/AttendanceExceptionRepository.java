package com.bluewhale.oa.attendance.domain.repository;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.domain.model.AttendanceException;
import com.bluewhale.oa.attendance.domain.model.AttendanceExceptionQuery;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 考勤异常仓储端口。
 *
 * @author BlueWhale
 */
public interface AttendanceExceptionRepository {

    /**
     * 按用户、日期与异常类型查询异常。
     *
     * @param userId 用户ID
     * @param workDate 工作日期
     * @param type 异常类型
     * @return 考勤异常
     */
    Optional<AttendanceException> find(Long userId, LocalDate workDate, String type);

    /**
     * 保存考勤异常。
     *
     * @param exception 考勤异常
     */
    void save(AttendanceException exception);

    /**
     * 更新考勤异常。
     *
     * @param exception 考勤异常
     */
    void update(AttendanceException exception);

    /**
     * 按主键查询考勤异常。
     *
     * @param id 主键ID
     * @return 考勤异常
     */
    Optional<AttendanceException> findById(Long id);

    /**
     * 分页查询考勤异常。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AttendanceException> page(AttendanceExceptionQuery query);
}
