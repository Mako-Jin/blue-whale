package com.bluewhale.mes.workforce.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MES 交接班实体，对应表 {@code mes_wf_handover}。
 *
 * <p>交接单号租户内唯一，状态由 {@code DRAFT} 确认后变为 {@code CONFIRMED}，
 * 确认时间与确认人留痕。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_wf_handover")
public class MesHandover extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 交接单号（租户内唯一） */
    private String handoverNo;

    /** 班组 ID */
    private Long teamId;

    /** 班组编码快照 */
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

    /** 状态：DRAFT/CONFIRMED */
    private String status;

    /** 确认人 */
    private String confirmedBy;

    /** 确认时间 */
    private LocalDateTime confirmedAt;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
