package com.bluewhale.mes.execution.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 现场异常事件实体，对应表 {@code mes_ex_exception}。
 *
 * <p>状态：{@code OPEN → ACKNOWLEDGED → RESOLVED → CLOSED}；必须绑定工单或任务之一，
 * 关闭前需填写处理结论。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_ex_exception")
public class MesExceptionEvent extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 异常单号（租户内唯一） */
    private String exceptionNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 任务 ID */
    private Long taskId;

    /** 类型：EQUIPMENT/MATERIAL/QUALITY/OTHER */
    private String exceptionType;

    /** 异常原因编码 */
    private String reasonCode;

    /** 异常原因名称快照 */
    private String reasonName;

    /** 异常描述 */
    private String description;

    /** 状态：OPEN/ACKNOWLEDGED/RESOLVED/CLOSED */
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

    /** 备注 */
    private String remark;
}
