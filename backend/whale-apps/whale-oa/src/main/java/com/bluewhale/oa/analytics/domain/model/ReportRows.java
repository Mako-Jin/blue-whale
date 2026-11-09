package com.bluewhale.oa.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报表聚合行（MyBatis 结果映射载体）。
 *
 * <p>各内部类对应一类报表的只读聚合结果，字段名与 SQL 别名按驼峰映射一一对应；
 * 应用层再转换为前端统一的行列结构。</p>
 *
 * @author BlueWhale
 */
public final class ReportRows {

    /** 工具类，禁止实例化。 */
    private ReportRows() {
    }

    /**
     * 考勤月报行。
     */
    @Data
    public static class AttendanceRow {

        /** 用户ID */
        private Long userId;

        /** 用户姓名快照 */
        private String userName;

        /** 出勤天数 */
        private Integer workDays;

        /** 迟到天数 */
        private Integer lateDays;

        /** 早退天数 */
        private Integer earlyDays;

        /** 缺卡天数 */
        private Integer absentDays;

        /** 迟到累计分钟 */
        private Integer lateMinutes;

        /** 早退累计分钟 */
        private Integer earlyMinutes;
    }

    /**
     * 审批实例行（供应用层计算时长分位数）。
     */
    @Data
    public static class ApprovalRow {

        /** 业务类型 */
        private String businessType;

        /** 审批状态 */
        private String status;

        /** 提交时间 */
        private LocalDateTime submittedAt;

        /** 完成时间 */
        private LocalDateTime finishedAt;
    }

    /**
     * 部门报销行。
     */
    @Data
    public static class ExpenseDeptRow {

        /** 部门ID */
        private Long deptId;

        /** 在职人数 */
        private Integer headcount;

        /** 报销单数 */
        private Integer expenseCount;

        /** 申请人去重数 */
        private Integer applicantCount;

        /** 报销总额 */
        private BigDecimal totalAmount;

        /** 单均金额 */
        private BigDecimal avgAmount;

        /** 人均报销 */
        private BigDecimal perCapitaAmount;
    }

    /**
     * 假期余额行。
     */
    @Data
    public static class LeaveBalanceRow {

        /** 假期类型ID */
        private Long leaveTypeId;

        /** 假期类型编码 */
        private String leaveTypeCode;

        /** 假期类型名称 */
        private String leaveTypeName;

        /** 涉及员工数 */
        private Integer userCount;

        /** 总额度 */
        private BigDecimal totalDays;

        /** 已使用 */
        private BigDecimal usedDays;

        /** 冻结中 */
        private BigDecimal frozenDays;

        /** 可用余额 */
        private BigDecimal availableDays;
    }

    /**
     * 用车利用率行。
     */
    @Data
    public static class VehicleRow {

        /** 车辆ID */
        private Long vehicleId;

        /** 车牌号 */
        private String plateNo;

        /** 预约次数 */
        private Integer bookingCount;

        /** 使用小时数 */
        private BigDecimal totalHours;
    }

    /**
     * 用章频率行。
     */
    @Data
    public static class SealRow {

        /** 印章ID */
        private Long sealId;

        /** 印章名称 */
        private String sealName;

        /** 用章次数 */
        private Integer useCount;

        /** 用章涉及文件数 */
        private Integer fileCount;
    }

    /**
     * 会议室利用率行。
     */
    @Data
    public static class MeetingRoomRow {

        /** 会议室ID */
        private Long roomId;

        /** 会议室名称 */
        private String roomName;

        /** 会议场次 */
        private Integer meetingCount;

        /** 占用小时数 */
        private BigDecimal totalHours;
    }

    /**
     * 公告阅读率行。
     */
    @Data
    public static class NoticeRow {

        /** 公告ID */
        private Long noticeId;

        /** 公告标题 */
        private String title;

        /** 发布时间 */
        private LocalDateTime publishTime;

        /** 接收人数 */
        private Integer recipientCount;

        /** 已读人数 */
        private Integer readCount;

        /** 阅读率（百分比） */
        private BigDecimal readRate;
    }

    /**
     * 网盘配额行。
     */
    @Data
    public static class DriveQuotaRow {

        /** 上传人用户ID */
        private Long uploaderId;

        /** 上传人姓名快照 */
        private String uploaderName;

        /** 文件数 */
        private Integer fileCount;

        /** 已用字节数 */
        private Long usedBytes;

        /** 已用容量（MB） */
        private BigDecimal usedMb;

        /** 配额占用百分比 */
        private BigDecimal usagePercent;
    }
}
