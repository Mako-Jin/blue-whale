package com.bluewhale.mes.workforce.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MES 排班分配实体，对应表 {@code mes_wf_shift_assignment}。
 *
 * <p>同一工位、班次日期与班次编码下仅允许一名主操作，状态 {@code ASSIGNED} 参与约束，
 * {@code CANCELLED} 视为释放。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_wf_shift_assignment")
public class MesShiftAssignment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 排班日期 */
    private LocalDate shiftDate;

    /** 班次编码 */
    private String shiftCode;

    /** 班组 ID */
    private Long teamId;

    /** 班组编码快照 */
    private String teamCode;

    /** 工位 ID */
    private Long stationId;

    /** 工位编码快照 */
    private String stationCode;

    /** 工作中心 ID */
    private Long workCenterId;

    /** 工作中心编码快照 */
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

    /** 状态：ASSIGNED/CANCELLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
