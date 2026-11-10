package com.bluewhale.oa.hr.application.view;

import lombok.Data;

import java.time.LocalDate;

/**
 * 请假内网只读视图（供 LMS 排期冲突提示）。
 *
 * <p>只输出判断冲突所需的字段：谁、哪段时间、什么假。请假事由与附件属于
 * 个人敏感信息，内部契约不外带。</p>
 *
 * @author BlueWhale
 */
@Data
public class LeaveConflictView {

    /** 请假单号 */
    private String code;

    /** 申请人用户 ID */
    private Long applicantId;

    /** 申请人账号 */
    private String applicant;

    /** 假期类型编码 */
    private String leaveType;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 申请状态（固定为 APPROVED） */
    private String status;
}
