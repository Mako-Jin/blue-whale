package com.bluewhale.mall.pricing.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运费模板视图。
 *
 * @author BlueWhale
 */
@Data
public class FreightTemplateView {

    /** 模板 ID */
    private Long id;

    /** 模板名称 */
    private String templateName;

    /** 计费方式 */
    private String chargeType;

    /** 固定运费金额 */
    private BigDecimal baseAmount;

    /** 包邮门槛 */
    private BigDecimal freeThreshold;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
