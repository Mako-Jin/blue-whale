package com.bluewhale.oa.analytics.infrastructure.persistence.mapper;

import com.bluewhale.oa.analytics.domain.model.ReportRows;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表只读聚合查询 Mapper。
 *
 * <p>仅做 SELECT 聚合，不写业务表；租户条件由 starter 的
 * {@code TenantLineInnerInterceptor} 统一追加。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface ReportQueryMapper {

    /**
     * 考勤月报聚合。
     *
     * @param from 起始日期
     * @param to 截止日期
     * @return 按用户汇总的考勤行
     */
    @Select("""
            SELECT d.user_id AS user_id,
                   d.user_name AS user_name,
                   COUNT(*) AS work_days,
                   SUM(CASE WHEN COALESCE(d.late_minutes, 0) > 0 THEN 1 ELSE 0 END) AS late_days,
                   SUM(CASE WHEN COALESCE(d.early_minutes, 0) > 0 THEN 1 ELSE 0 END) AS early_days,
                   SUM(CASE WHEN COALESCE(d.absent, 0) = 1 THEN 1 ELSE 0 END) AS absent_days,
                   COALESCE(SUM(d.late_minutes), 0) AS late_minutes,
                   COALESCE(SUM(d.early_minutes), 0) AS early_minutes
            FROM oa_att_daily d
            WHERE d.deleted = 0
              AND d.work_date BETWEEN #{from} AND #{to}
            GROUP BY d.user_id, d.user_name
            ORDER BY absent_days DESC, late_days DESC, d.user_id ASC
            """)
    List<ReportRows.AttendanceRow> attendanceMonthly(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 期间内审批实例明细。
     *
     * @param from 起始日期
     * @param to 截止日期
     * @return 审批实例行
     */
    @Select("""
            SELECT a.business_type AS business_type,
                   a.status AS status,
                   a.submitted_at AS submitted_at,
                   a.finished_at AS finished_at
            FROM oa_approval_instance a
            WHERE a.deleted = 0
              AND a.submitted_at IS NOT NULL
              AND a.submitted_at >= #{from}
              AND a.submitted_at < DATE_ADD(#{to}, INTERVAL 1 DAY)
            ORDER BY a.business_type ASC, a.submitted_at ASC
            """)
    List<ReportRows.ApprovalRow> approvalInstances(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 部门报销聚合。
     *
     * @param from 起始日期
     * @param to 截止日期
     * @return 部门报销行
     */
    @Select("""
            SELECT e.dept_id AS dept_id,
                   (SELECT COUNT(*) FROM oa_employee_profile p
                     WHERE p.dept_id = e.dept_id AND p.deleted = 0
                       AND COALESCE(p.employment_status, 'ACTIVE') <> 'LEFT') AS headcount,
                   COUNT(*) AS expense_count,
                   COUNT(DISTINCT e.applicant_id) AS applicant_count,
                   ROUND(SUM(e.total_amount), 2) AS total_amount,
                   ROUND(AVG(e.total_amount), 2) AS avg_amount,
                   ROUND(SUM(e.total_amount) / NULLIF((SELECT COUNT(*)
                     FROM oa_employee_profile p2
                     WHERE p2.dept_id = e.dept_id AND p2.deleted = 0
                       AND COALESCE(p2.employment_status, 'ACTIVE') <> 'LEFT'), 0), 2) AS per_capita_amount
            FROM oa_expense e
            WHERE e.deleted = 0
              AND e.status = 'APPROVED'
              AND e.submit_time >= #{from}
              AND e.submit_time < DATE_ADD(#{to}, INTERVAL 1 DAY)
            GROUP BY e.dept_id
            ORDER BY total_amount DESC
            """)
    List<ReportRows.ExpenseDeptRow> expenseByDept(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 假期余额聚合。
     *
     * @param year 额度年份
     * @return 假期余额行
     */
    @Select("""
            SELECT b.leave_type_id AS leave_type_id,
                   t.code AS leave_type_code,
                   t.name AS leave_type_name,
                   COUNT(DISTINCT b.user_id) AS user_count,
                   ROUND(SUM(b.total_days), 2) AS total_days,
                   ROUND(SUM(b.used_days), 2) AS used_days,
                   ROUND(SUM(b.frozen_days), 2) AS frozen_days,
                   ROUND(SUM(b.total_days - b.used_days - b.frozen_days), 2) AS available_days
            FROM oa_leave_balance b
            LEFT JOIN oa_leave_type t ON t.id = b.leave_type_id AND t.deleted = 0
            WHERE b.deleted = 0
              AND b.balance_year = #{year}
            GROUP BY b.leave_type_id, t.code, t.name
            ORDER BY t.code ASC
            """)
    List<ReportRows.LeaveBalanceRow> leaveBalance(@Param("year") int year);

    /**
     * 用车利用率聚合。
     *
     * @param from 起始日期
     * @param to 截止日期
     * @return 车辆使用行
     */
    @Select("""
            SELECT b.vehicle_id AS vehicle_id,
                   v.plate_no AS plate_no,
                   COUNT(*) AS booking_count,
                   ROUND(SUM(TIMESTAMPDIFF(MINUTE, b.start_time, b.end_time)) / 60, 2) AS total_hours
            FROM oa_vehicle_booking b
            LEFT JOIN oa_vehicle v ON v.id = b.vehicle_id AND v.deleted = 0
            WHERE b.deleted = 0
              AND b.status = 'APPROVED'
              AND b.start_time >= #{from}
              AND b.start_time < DATE_ADD(#{to}, INTERVAL 1 DAY)
            GROUP BY b.vehicle_id, v.plate_no
            ORDER BY total_hours DESC
            """)
    List<ReportRows.VehicleRow> vehicleUtilization(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 用章频率聚合。
     *
     * @param from 起始日期
     * @param to 截止日期
     * @return 印章使用行
     */
    @Select("""
            SELECT s.seal_id AS seal_id,
                   MAX(s.seal_name) AS seal_name,
                   COUNT(*) AS use_count,
                   COALESCE(SUM(s.file_count), 0) AS file_count
            FROM oa_seal_use s
            WHERE s.deleted = 0
              AND s.status = 'APPROVED'
              AND s.use_time >= #{from}
              AND s.use_time < DATE_ADD(#{to}, INTERVAL 1 DAY)
            GROUP BY s.seal_id
            ORDER BY use_count DESC
            """)
    List<ReportRows.SealRow> sealUtilization(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 会议室利用率聚合。
     *
     * @param from 起始日期
     * @param to 截止日期
     * @return 会议室使用行
     */
    @Select("""
            SELECT m.room_id AS room_id,
                   r.name AS room_name,
                   COUNT(*) AS meeting_count,
                   ROUND(SUM(TIMESTAMPDIFF(MINUTE, m.start_time, m.end_time)) / 60, 2) AS total_hours
            FROM oa_meeting m
            LEFT JOIN oa_meeting_room r ON r.id = m.room_id AND r.deleted = 0
            WHERE m.deleted = 0
              AND m.status IN ('SCHEDULED', 'FINISHED')
              AND m.start_time >= #{from}
              AND m.start_time < DATE_ADD(#{to}, INTERVAL 1 DAY)
            GROUP BY m.room_id, r.name
            ORDER BY total_hours DESC
            """)
    List<ReportRows.MeetingRoomRow> meetingRoomUtilization(@Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * 公告阅读率聚合。
     *
     * @param from 起始日期
     * @param to 截止日期
     * @return 公告阅读行
     */
    @Select("""
            SELECT n.id AS notice_id,
                   n.title AS title,
                   n.publish_time AS publish_time,
                   COUNT(r.id) AS recipient_count,
                   COALESCE(SUM(CASE WHEN r.read_flag = 1 THEN 1 ELSE 0 END), 0) AS read_count,
                   ROUND(COALESCE(SUM(CASE WHEN r.read_flag = 1 THEN 1 ELSE 0 END), 0) * 100
                         / NULLIF(COUNT(r.id), 0), 2) AS read_rate
            FROM oa_notice n
            LEFT JOIN oa_notice_recipient r ON r.notice_id = n.id AND r.deleted = 0
            WHERE n.deleted = 0
              AND n.status = 'PUBLISHED'
              AND n.publish_time >= #{from}
              AND n.publish_time < DATE_ADD(#{to}, INTERVAL 1 DAY)
            GROUP BY n.id, n.title, n.publish_time
            ORDER BY read_rate ASC
            """)
    List<ReportRows.NoticeRow> noticeReadRate(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 网盘配额聚合（当前时点）。
     *
     * @param quotaBytes 单用户配额字节数
     * @return 上传人配额占用行
     */
    @Select("""
            SELECT f.uploader_id AS uploader_id,
                   MAX(f.uploader_name) AS uploader_name,
                   COUNT(*) AS file_count,
                   COALESCE(SUM(f.file_size), 0) AS used_bytes,
                   ROUND(COALESCE(SUM(f.file_size), 0) / 1048576, 2) AS used_mb,
                   ROUND(COALESCE(SUM(f.file_size), 0) * 100 / NULLIF(#{quotaBytes}, 0), 2) AS usage_percent
            FROM oa_drive_file f
            WHERE f.deleted = 0
            GROUP BY f.uploader_id
            ORDER BY used_bytes DESC
            """)
    List<ReportRows.DriveQuotaRow> driveQuota(@Param("quotaBytes") long quotaBytes);
}
