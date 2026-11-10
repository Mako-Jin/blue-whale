package com.bluewhale.mes.execution.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 现场异常视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesExceptionVO {

    /** 主键 ID */
    private Long id;

    /** 异常单号 */
    private String exceptionNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 任务 ID */
    private Long taskId;

    /** 异常类型 */
    private String exceptionType;

    /** 异常原因编码 */
    private String reasonCode;

    /** 异常原因名称 */
    private String reasonName;

    /** 异常描述 */
    private String description;

    /** 状态 */
    private String status;

    /** 上报人 */
    private String reportedBy;

    /** 发生时间 */
    private LocalDateTime occurredAt;

    /** 解决时间 */
    private LocalDateTime resolvedAt;

    /** 处理结论 */
    private String resolution;

    /** 升级层级 */
    private Integer escalationLevel;
}
