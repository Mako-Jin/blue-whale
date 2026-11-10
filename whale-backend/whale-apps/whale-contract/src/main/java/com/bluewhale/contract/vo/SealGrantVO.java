package com.bluewhale.contract.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用章授权视图对象。
 *
 * @author BlueWhale
 */
@Data
public class SealGrantVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 授权ID */
    private String id;

    /** 印章ID */
    private String sealId;

    /** 被授权用户ID */
    private String granteeUserId;

    /** 适用合同类别 */
    private String contractCategory;

    /** 单份合同金额上限 */
    private BigDecimal maxAmount;

    /** 授权生效日期 */
    private LocalDate validFrom;

    /** 授权失效日期 */
    private LocalDate validTo;

    /** 状态 */
    private String status;

    /** 授权操作人 */
    private String grantedBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
