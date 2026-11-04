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
    @NotNull(message = "NCR ID 不能为空")
    private Long ncrId;

    /** 处置类型：REWORK / REPAIR / SCRAP / CONCESSION */
    @NotNull(message = "处置类型不能为空")
    @Pattern(regexp = "REWORK|REPAIR|SCRAP|CONCESSION",
            message = "处置类型仅支持 REWORK / REPAIR / SCRAP / CONCESSION")
    private String dispositionType;

    /** 处置数量（空则取 NCR 不良数量；填写时必须与之一致） */
    private BigDecimal quantity;

    /** 处置原因 */
    @Size(max = 255, message = "处置原因长度不能超过 255")
    private String reason;

    /** 审批人 */
    @Size(max = 64, message = "审批人长度不能超过 64")
    private String approvedBy;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
