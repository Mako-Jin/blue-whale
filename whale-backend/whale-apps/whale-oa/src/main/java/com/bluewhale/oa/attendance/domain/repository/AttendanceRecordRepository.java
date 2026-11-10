package com.bluewhale.oa.attendance.domain.repository;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.domain.model.AttendanceRecord;
import com.bluewhale.oa.attendance.domain.model.AttendanceRecordQuery;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 原始打卡记录仓储端口。
 *
 * @author BlueWhale
 */
public interface AttendanceRecordRepository {

    /**
     * 判断同一用户同时间同类型的打卡是否已存在。
     *
     * @param userId 用户ID
     * @param punchTime 打卡时间
     * @param punchType 打卡类型
     * @return 是否已存在
     */
    boolean exists(Long userId, LocalDateTime punchTime, String punchType);

    /**
     * 保存打卡记录。
     *
     * @param record 打卡记录
     */
    void save(AttendanceRecord record);

    /**
     * 分页查询打卡记录。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AttendanceRecord> page(AttendanceRecordQuery query);

    /**
     * 按用户与日期区间查询打卡记录。
     *
     * @param userId 用户ID
     * @param dateFrom 起始日期
     * @param dateTo 结束日期
     * @return 打卡记录列表
     */
    List<AttendanceRecord> listByUser(Long userId, LocalDate dateFrom, LocalDate dateTo);
}
