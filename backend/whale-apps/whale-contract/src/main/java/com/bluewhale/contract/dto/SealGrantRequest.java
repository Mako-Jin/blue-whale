package com.bluewhale.contract.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 用章授权请求。
 *
 * @author BlueWhale
 */
@Data
public class SealGrantRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 被授权用户ID */
    @NotNull(message = "被授权用户不能为空")
    private Long granteeUserId;

    /** 适用合同类别（空表示全类别） */
    private String contractCategory;

    /** 单份合同金额上限（空表示不限） */
    private BigDecimal maxAmount;

    /** 授权生效日期 */
    private LocalDate validFrom;

    /** 授权失效日期 */
    private LocalDate validTo;
}
