package com.bluewhale.mes.workforce.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交接班视图
 *
 * @author BlueWhale
 */
@Data
public class MesHandoverVO {

    /** 交接 ID */
    private Long id;

    /** 交接单号 */
    private String handoverNo;

    /** 班组 ID */
    private Long teamId;

    /** 班组编码 */
    private String teamCode;

    /** 交班班次编码 */
    private String fromShiftCode;

    /** 接班班次编码 */
    private String toShiftCode;

    /** 交接日期 */
    private LocalDate handoverDate;

    /** 交班人 ID */
    private Long fromEmployeeId;

    /** 交班人姓名 */
    private String fromEmployeeName;

    /** 接班人 ID */
    private Long toEmployeeId;

    /** 接班人姓名 */
    private String toEmployeeName;

    /** 交接内容 */
    private String content;

    /** 遗留问题 */
    private String pendingIssues;

    /** 状态 */
    private String status;

    /** 确认人 */
    private String confirmedBy;

    /** 确认时间 */
    private LocalDateTime confirmedAt;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
