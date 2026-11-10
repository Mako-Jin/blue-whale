package com.bluewhale.oa.analytics.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * OA 报表类型。
 *
 * <p>九类报表覆盖人力资源、审批效率、费用、行政资源与协同使用情况；
 * 编码同时作为前端路由参数与快照唯一键的一部分。</p>
 *
 * @author BlueWhale
 */
public enum ReportType {

    /** 考勤月报（按人汇总迟到、早退与缺卡） */
    ATTENDANCE_MONTHLY("考勤月报"),

    /** 审批效率（按业务类型统计耗时与积压） */
    APPROVAL_EFFICIENCY("审批效率"),

    /** 部门人均报销 */
    EXPENSE_DEPT("部门人均报销"),

    /** 假期余额（按假期类型汇总） */
    LEAVE_BALANCE("假期余额"),

    /** 用车利用率 */
    VEHICLE_UTILIZATION("用车利用率"),

    /** 用章频率 */
    SEAL_UTILIZATION("用章频率"),

    /** 会议室利用率 */
    MEETING_ROOM_UTILIZATION("会议室利用率"),

    /** 公告阅读率 */
    NOTICE_READ_RATE("公告阅读率"),

    /** 网盘配额占用 */
    DRIVE_QUOTA("网盘配额");

    /** 报表中文名称 */
    private final String title;

    /**
     * 构造报表类型。
     *
     * @param title 报表中文名称
     */
    ReportType(String title) {
        this.title = title;
    }

    /**
     * 获取报表中文名称。
     *
     * @return 报表名称
     */
    public String getTitle() {
        return title;
    }

    /**
     * 解析报表编码（大小写不敏感）。
     *
     * @param code 报表编码
     * @return 报表类型
     */
    public static ReportType of(String code) {
        if (code == null || code.isBlank()) {
            throw OaErrorCode.exception(OaErrorCode.REPORT_INVALID, "报表编码不能为空");
        }
        try {
            return ReportType.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw OaErrorCode.exception(OaErrorCode.REPORT_INVALID, "未知报表类型：" + code);
        }
    }
}
