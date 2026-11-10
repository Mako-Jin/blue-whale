package com.bluewhale.oa.analytics.application.support;

import com.bluewhale.oa.analytics.application.view.ReportViews;
import com.bluewhale.oa.analytics.domain.model.ReportType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 报表目录：列定义与说明的唯一事实源。
 *
 * @author BlueWhale
 */
public final class ReportCatalog {

    /** 各类报表的列定义 */
    private static final Map<ReportType, List<ReportViews.Column>> COLUMNS =
            new EnumMap<>(ReportType.class);

    /** 各类报表的说明 */
    private static final Map<ReportType, String> DESCRIPTIONS = new EnumMap<>(ReportType.class);

    static {
        COLUMNS.put(ReportType.ATTENDANCE_MONTHLY, List.of(
                column("userId", "用户ID"),
                column("userName", "姓名"),
                column("workDays", "出勤天数"),
                column("lateDays", "迟到天数"),
                column("earlyDays", "早退天数"),
                column("absentDays", "缺卡天数"),
                column("lateMinutes", "迟到累计分钟"),
                column("earlyMinutes", "早退累计分钟")));
        DESCRIPTIONS.put(ReportType.ATTENDANCE_MONTHLY, "按人汇总区间内的出勤、迟到、早退与缺卡天数");

        COLUMNS.put(ReportType.APPROVAL_EFFICIENCY, List.of(
                column("businessType", "业务类型"),
                column("totalCount", "申请总数"),
                column("approvedCount", "已通过"),
                column("rejectedCount", "已驳回"),
                column("runningCount", "审批中"),
                column("avgMinutes", "平均时长（分钟）"),
                column("p50Minutes", "中位数时长（分钟）"),
                column("p90Minutes", "P90 时长（分钟）"),
                column("overdue48Count", "超 48 小时未办")));
        DESCRIPTIONS.put(ReportType.APPROVAL_EFFICIENCY, "按业务类型统计审批耗时分位数与积压");

        COLUMNS.put(ReportType.EXPENSE_DEPT, List.of(
                column("deptId", "部门ID"),
                column("headcount", "在职人数"),
                column("expenseCount", "报销单数"),
                column("applicantCount", "申请人数"),
                column("totalAmount", "报销总额"),
                column("avgAmount", "单均金额"),
                column("perCapitaAmount", "人均报销")));
        DESCRIPTIONS.put(ReportType.EXPENSE_DEPT, "按部门汇总已通过报销金额与在职人数，计算人均报销");

        COLUMNS.put(ReportType.LEAVE_BALANCE, List.of(
                column("leaveTypeCode", "假期编码"),
                column("leaveTypeName", "假期名称"),
                column("userCount", "员工数"),
                column("totalDays", "总额度"),
                column("usedDays", "已使用"),
                column("frozenDays", "冻结中"),
                column("availableDays", "可用余额")));
        DESCRIPTIONS.put(ReportType.LEAVE_BALANCE, "按假期类型汇总年度额度、已使用与冻结情况");

        COLUMNS.put(ReportType.VEHICLE_UTILIZATION, List.of(
                column("vehicleId", "车辆ID"),
                column("plateNo", "车牌号"),
                column("bookingCount", "用车次数"),
                column("totalHours", "使用小时数")));
        DESCRIPTIONS.put(ReportType.VEHICLE_UTILIZATION, "按车辆统计区间内已通过预约次数与使用时长");

        COLUMNS.put(ReportType.SEAL_UTILIZATION, List.of(
                column("sealId", "印章ID"),
                column("sealName", "印章名称"),
                column("useCount", "用章次数"),
                column("fileCount", "用章文件数")));
        DESCRIPTIONS.put(ReportType.SEAL_UTILIZATION, "按印章统计区间内已通过用章次数与文件数");

        COLUMNS.put(ReportType.MEETING_ROOM_UTILIZATION, List.of(
                column("roomId", "会议室ID"),
                column("roomName", "会议室"),
                column("meetingCount", "会议场次"),
                column("totalHours", "占用小时数")));
        DESCRIPTIONS.put(ReportType.MEETING_ROOM_UTILIZATION, "按会议室统计区间内会议场次与占用时长");

        COLUMNS.put(ReportType.NOTICE_READ_RATE, List.of(
                column("noticeId", "公告ID"),
                column("title", "标题"),
                column("publishTime", "发布时间"),
                column("recipientCount", "接收人数"),
                column("readCount", "已读人数"),
                column("readRate", "阅读率（%）")));
        DESCRIPTIONS.put(ReportType.NOTICE_READ_RATE, "统计区间内已发布公告的接收与阅读情况");

        COLUMNS.put(ReportType.DRIVE_QUOTA, List.of(
                column("uploaderId", "用户ID"),
                column("uploaderName", "上传人"),
                column("fileCount", "文件数"),
                column("usedMb", "已用容量（MB）"),
                column("usagePercent", "配额占用（%）")));
        DESCRIPTIONS.put(ReportType.DRIVE_QUOTA, "按上传人统计网盘已用容量与配额占用（当前时点）");
    }

    /** 工具类，禁止实例化。 */
    private ReportCatalog() {
    }

    /**
     * 构造列定义。
     *
     * @param key 字段键
     * @param label 列标题
     * @return 列定义
     */
    private static ReportViews.Column column(String key, String label) {
        return new ReportViews.Column(key, label);
    }

    /**
     * 获取报表列定义。
     *
     * @param type 报表类型
     * @return 列定义
     */
    public static List<ReportViews.Column> columns(ReportType type) {
        return COLUMNS.getOrDefault(type, List.of());
    }

    /**
     * 获取报表目录。
     *
     * @return 目录项
     */
    public static List<ReportViews.CatalogItem> catalog() {
        List<ReportViews.CatalogItem> items = new ArrayList<>();
        for (ReportType type : ReportType.values()) {
            ReportViews.CatalogItem item = new ReportViews.CatalogItem();
            item.setCode(type.name());
            item.setTitle(type.getTitle());
            item.setDescription(DESCRIPTIONS.getOrDefault(type, ""));
            item.setColumns(columns(type));
            items.add(item);
        }
        return items;
    }
}
