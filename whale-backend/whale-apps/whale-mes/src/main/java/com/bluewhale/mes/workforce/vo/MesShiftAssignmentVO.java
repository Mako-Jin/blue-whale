package com.bluewhale.mes.workforce.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排班分配视图
 *
 * @author BlueWhale
 */
@Data
public class MesShiftAssignmentVO {

    /** 排班 ID */
    private Long id;

    /** 排班日期 */
    private LocalDate shiftDate;

    /** 班次编码 */
    private String shiftCode;

    /** 班组 ID */
    private Long teamId;

    /** 班组编码 */
    private String teamCode;

    /** 工位 ID */
    private Long stationId;

    /** 工位编码 */
    private String stationCode;

    /** 工作中心 ID */
    private Long workCenterId;

    /** 工作中心编码 */
    private String workCenterCode;

    /** 员工 ID */
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;

    /** 是否主操作：0 否 / 1 是 */
    private Integer primaryOperator;

    /** 班次开始时间 */
    private LocalDateTime startAt;

    /** 班次结束时间 */
    private LocalDateTime endAt;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
