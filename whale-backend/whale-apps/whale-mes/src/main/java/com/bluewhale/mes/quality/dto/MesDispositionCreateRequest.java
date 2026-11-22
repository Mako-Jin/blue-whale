package com.bluewhale.mes.quality.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 不合格品处置创建请求
 *
 * @author BlueWhale
 */
@Data
public class MesDispositionCreateRequest {

    /** NCR ID */
    @NotNull(message = "{validation.mes.mes-disposition.ncr-id-required}")
    private Long ncrId;

    /** 处置类型：REWORK / REPAIR / SCRAP / CONCESSION */
    @NotNull(message = "{validation.mes.mes-disposition.disposition-type-required}")
    @Pattern(regexp = "REWORK|REPAIR|SCRAP|CONCESSION",
            message = "{validation.mes.mes-disposition.disposition-type-pattern}")
    private String dispositionType;

    /** 处置数量（空则取 NCR 不良数量；填写时必须与之一致） */
    private BigDecimal quantity;

    /** 处置原因 */
    @Size(max = 255, message = "{validation.mes.mes-disposition.reason-size}")
    private String reason;

    /** 审批人 */
    @Size(max = 64, message = "{validation.mes.mes-disposition.approved-by-size}")
    private String approvedBy;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-disposition.remark-size}")
    private String remark;
}
