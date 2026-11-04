package com.bluewhale.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 不合格品处置实体，对应表 {@code mes_qc_disposition}。
 *
 * <p>一个 NCR 仅允许一条处置记录；处置数量必须等于 NCR 不良数量。
 * 处置类型 {@code REWORK} 执行时自动生成返工单。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_qc_disposition")
public class MesDisposition extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 处置单号（租户内唯一） */
    private String dispositionNo;

    /** NCR ID */
    private Long ncrId;

    /** NCR 编号快照 */
    private String ncrNo;

    /** 处置类型：REWORK / REPAIR / SCRAP / CONCESSION */
    private String dispositionType;

    /** 处置数量 */
    private BigDecimal quantity;

    /** 处置原因 */
    private String reason;

    /** 审批人 */
    private String approvedBy;

    /** 状态：CONFIRMED / EXECUTED / CANCELLED */
    private String status;

    /** 执行时间 */
    private LocalDateTime executedAt;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
