package com.bluewhale.mes.workforce.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MES 工时记录实体，对应表 {@code mes_wf_work_hour}。
 *
 * <p>净工时秒数 = 结束时间 - 开始时间 - 暂停秒数，且必须非负；
 * {@code REPORT} 来源与报工口径一致，{@code MANUAL} 来源用于手工补录。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_wf_work_hour")
public class MesWorkHour extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID */
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;

    /** 工作日期 */
    private LocalDate workDate;

    /** 任务 ID */
    private Long taskId;

    /** 任务编号快照 */
    private String taskNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码快照 */
    private String workOrderCode;

    /** 开始时间 */
    private LocalDateTime startAt;

    /** 结束时间 */
    private LocalDateTime endAt;

    /** 暂停秒数 */
    private Long pauseSeconds;

    /** 净工时秒数 */
    private Long netSeconds;

    /** 来源：REPORT/MANUAL */
    private String source;

    /** 状态：VALID/VOID */
    private String status;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
