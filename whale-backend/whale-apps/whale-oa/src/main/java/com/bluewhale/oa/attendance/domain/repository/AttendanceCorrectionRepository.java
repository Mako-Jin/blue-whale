package com.bluewhale.oa.attendance.domain.repository;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.domain.model.AttendanceCorrection;
import com.bluewhale.oa.attendance.domain.model.AttendanceCorrectionQuery;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 补卡申请仓储端口。
 *
 * @author BlueWhale
 */
public interface AttendanceCorrectionRepository {

    /**
     * 保存补卡申请。
     *
     * @param correction 补卡申请
     */
    void save(AttendanceCorrection correction);

    /**
     * 按主键查询补卡申请。
     *
     * @param id 主键ID
     * @return 补卡申请
     */
    Optional<AttendanceCorrection> findById(Long id);

    /**
     * 按单号查询补卡申请。
     *
     * @param code 单号
     * @return 补卡申请
     */
    Optional<AttendanceCorrection> findByCode(String code);

    /**
     * 查询用户指定日期与类型的待审批补卡。
     *
     * @param userId 用户ID
     * @param workDate 工作日期
     * @param punchType 打卡类型
     * @return 待审批补卡
     */
    Optional<AttendanceCorrection> findPending(Long userId, LocalDate workDate, String punchType);

    /**
     * 统计某月补卡申请数量。
     *
     * @param userId 用户ID
     * @param monthStart 月份起始日期（含）
     * @param monthEnd 月份结束日期（不含）
     * @return 申请数量
     */
    long countByMonth(Long userId, LocalDate monthStart, LocalDate monthEnd);

    /**
     * 分页查询补卡申请。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AttendanceCorrection> page(AttendanceCorrectionQuery query);
}
