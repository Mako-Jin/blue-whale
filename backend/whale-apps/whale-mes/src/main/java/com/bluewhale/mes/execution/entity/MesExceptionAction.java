package com.bluewhale.mes.execution.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 异常处理动作实体，对应表 {@code mes_ex_exception_action}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_ex_exception_action")
public class MesExceptionAction extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 异常事件 ID */
    private Long exceptionId;

    /** 动作：REPORT/ACKNOWLEDGE/RESOLVE/CLOSE */
    private String actionType;

    /** 原状态 */
    private String fromStatus;

    /** 新状态 */
    private String toStatus;

    /** 操作人 */
    private String actionBy;

    /** 操作时间 */
    private LocalDateTime actionTime;

    /** 备注 */
    private String remark;
}
