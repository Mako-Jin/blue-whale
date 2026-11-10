package com.bluewhale.mall.catalog.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动视图。
 *
 * @author BlueWhale
 */
@Data
public class ActivityView {

    /** 主键 */
    private Long id;

    /** 活动编码 */
    private String activityCode;

    /** 活动名称 */
    private String activityName;

    /** 适用渠道 ID（空 = 全渠道） */
    private Long channelId;

    /** 活动 SKU ID */
    private Long skuId;

    /** SKU 编码 */
    private String skuCode;

    /** 活动价 */
    private BigDecimal activityPrice;

    /** 生效时间 */
    private LocalDateTime validFrom;

    /** 失效时间 */
    private LocalDateTime validTo;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
