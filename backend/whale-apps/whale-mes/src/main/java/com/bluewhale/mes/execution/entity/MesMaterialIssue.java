package com.bluewhale.mes.execution.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 领料/补料单实体，对应表 {@code mes_ex_material_issue}。
 *
 * <p>M1 只记录领料事实与明细，线边仓库存扣减（含倒冲、超领审批）在 M2 物料与追溯落地。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_ex_material_issue")
public class MesMaterialIssue extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 领料单号（租户内唯一） */
    private String issueNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 任务 ID */
    private Long taskId;

    /** 领料仓库编码 */
    private String warehouseCode;

    /** 类型：ISSUE 领料 / SUPPLEMENT 补料 */
    private String issueType;

    /** 状态：ISSUED */
    private String status;

    /** 领料人 */
    private String issuedBy;

    /** 领料时间 */
    private LocalDateTime issuedAt;

    /** 补料原因 */
    private String reason;

    /** 备注 */
    private String remark;
}
