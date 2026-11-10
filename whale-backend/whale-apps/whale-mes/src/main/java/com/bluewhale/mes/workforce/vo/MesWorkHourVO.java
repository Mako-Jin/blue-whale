package com.bluewhale.mes.workforce.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工时记录视图
 *
 * @author BlueWhale
 */
@Data
public class MesWorkHourVO {

    /** 工时 ID */
    private Long id;

    /** 员工 ID */
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;

    /** 工作日期 */
    private LocalDate workDate;

    /** 任务 ID */
    private Long taskId;

    /** 任务编号 */
    private String taskNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码 */
    private String workOrderCode;

    /** 开始时间 */
    private LocalDateTime startAt;

    /** 结束时间 */
    private LocalDateTime endAt;

    /** 暂停秒数 */
    private Long pauseSeconds;

    /** 净工时秒数 */
    private Long netSeconds;

    /** 来源 */
    private String source;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
