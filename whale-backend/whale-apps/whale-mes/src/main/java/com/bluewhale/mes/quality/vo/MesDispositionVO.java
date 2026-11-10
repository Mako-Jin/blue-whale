package com.bluewhale.mes.quality.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 不合格品处置视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesDispositionVO {

    /** 处置 ID */
    private Long id;

    /** 处置单号 */
    private String dispositionNo;

    /** NCR ID */
    private Long ncrId;

    /** NCR 编号 */
    private String ncrNo;

    /** 处置类型 */
    private String dispositionType;

    /** 处置数量 */
    private BigDecimal quantity;

    /** 处置原因 */
    private String reason;

    /** 审批人 */
    private String approvedBy;

    /** 状态 */
    private String status;

    /** 执行时间 */
    private LocalDateTime executedAt;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
