package com.bluewhale.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 不合格品记录 NCR 实体，对应表 {@code mes_qc_ncr}。
 *
 * <p>检验判定 {@code FAILED} 时自动生成；状态机为
 * {@code OPEN → DISPOSITION → REWORK/REPAIR/SCRAP/CONCESSION → CLOSED}。
 * 关闭必须给出结论；处置数量必须等于不良数量。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_qc_ncr")
public class MesNonconformingRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** NCR 编号（租户内唯一） */
    private String ncrNo;

    /** 来源：INSPECTION / REPORT / MANUAL */
    private String sourceType;

    /** 来源检验任务 ID */
    private Long inspectionTaskId;

    /** 来源检验任务号快照 */
    private String inspectionTaskNo;

    /** 来源报工记录 ID */
    private Long reportId;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码快照 */
    private String workOrderCode;

    /** 工序任务 ID */
    private Long execTaskId;

    /** 工序任务号快照 */
    private String execTaskNo;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 批次号 */
    private String batchNo;

    /** 工序编码 */
    private String operationCode;

    /** 缺陷代码 */
    private String defectCode;

    /** 缺陷名称快照 */
    private String defectName;

    /** 不良数量 */
    private BigDecimal defectQuantity;

    /** 状态：OPEN / DISPOSITION / REWORK / REPAIR / SCRAP / CONCESSION / CLOSED */
    private String status;

    /** 关闭结论 */
    private String conclusion;

    /** 关闭人 */
    private String closedBy;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
