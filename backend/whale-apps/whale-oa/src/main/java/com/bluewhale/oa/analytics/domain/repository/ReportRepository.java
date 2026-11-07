package com.bluewhale.oa.analytics.domain.repository;

import com.bluewhale.oa.analytics.domain.model.ReportExportLog;
import com.bluewhale.oa.analytics.domain.model.ReportRows;
import com.bluewhale.oa.analytics.domain.model.ReportSnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 报表仓储端口：只读聚合 + 快照/导出记录。
 *
 * @author BlueWhale
 */
public interface ReportRepository {

    /**
     * 考勤月报聚合。
     *
     * @param from 起始日期
     * @param to 截止日期
     * @return 按用户汇总的考勤行
     */
    List<ReportRows.AttendanceRow> attendanceMonthly(LocalDate from, LocalDate to);

    /**
     * 期间内审批实例明细（供应用层计算时长分位数）。
     *
     * @param from 起始日期
     * @param to 截止日期
     * @return 审批实例行
     */
    List<ReportRows.ApprovalRow> approvalInstances(LocalDate from, LocalDate to);

    /**
     * 部门报销聚合。
     *
     * @param from 起始日期
     * @param to 截止日期
     * @return 部门报销行
     */
    List<ReportRows.ExpenseDeptRow> expenseByDept(LocalDate from, LocalDate to);

    /**
     * 假期余额聚合。
     *
     * @param year 额度年份
     * @return 假期余额行
     */
    List<ReportRows.LeaveBalanceRow> leaveBalance(int year);

    /**
     * 用车利用率聚合。
     *
     * @param from 起始日期
     * @param to 截止日期
     * @return 车辆使用行
     */
    List<ReportRows.VehicleRow> vehicleUtilization(LocalDate from, LocalDate to);

    /**
     * 用章频率聚合。
     *
     * @param from 起始日期
     * @param to 截止日期
     * @return 印章使用行
     */
    List<ReportRows.SealRow> sealUtilization(LocalDate from, LocalDate to);

    /**
     * 会议室利用率聚合。
     *
     * @param from 起始日期
     * @param to 截止日期
     * @return 会议室使用行
     */
    List<ReportRows.MeetingRoomRow> meetingRoomUtilization(LocalDate from, LocalDate to);

    /**
     * 公告阅读率聚合。
     *
     * @param from 起始日期
     * @param to 截止日期
     * @return 公告阅读行
     */
    List<ReportRows.NoticeRow> noticeReadRate(LocalDate from, LocalDate to);

    /**
     * 网盘配额聚合（当前时点）。
     *
     * @param quotaBytes 单用户配额字节数
     * @return 上传人配额占用行
     */
    List<ReportRows.DriveQuotaRow> driveQuota(long quotaBytes);

    /**
     * 按报表编码与期间查找快照。
     *
     * @param reportCode 报表编码
     * @param period 统计期间
     * @return 快照
     */
    Optional<ReportSnapshot> findSnapshot(String reportCode, String period);

    /**
     * 保存快照（同编码同期间已存在时覆盖）。
     *
     * @param snapshot 快照
     */
    void saveSnapshot(ReportSnapshot snapshot);

    /**
     * 分页查询快照元信息。
     *
     * @param reportCode 报表编码（可为空）
     * @param current 页码
     * @param size 每页条数
     * @return 快照列表
     */
    List<ReportSnapshot> pageSnapshots(String reportCode, long current, long size);

    /**
     * 统计快照总数。
     *
     * @param reportCode 报表编码（可为空）
     * @return 总数
     */
    long countSnapshots(String reportCode);

    /**
     * 按主键查找快照。
     *
     * @param id 快照ID
     * @return 快照
     */
    Optional<ReportSnapshot> findSnapshotById(Long id);

    /**
     * 追加导出记录。
     *
     * @param log 导出记录
     */
    void saveExportLog(ReportExportLog log);
}
