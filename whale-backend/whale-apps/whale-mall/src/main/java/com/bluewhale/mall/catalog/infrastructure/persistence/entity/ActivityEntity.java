package com.bluewhale.mall.catalog.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 限时特价活动实体，对应表 {@code mall_activity}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_activity")
public class ActivityEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 活动编码（租户内唯一） */
    private String activityCode;

    /** 活动名称 */
    private String activityName;

    /** 适用渠道 ID（空 = 全渠道） */
    private Long channelId;

    /** 活动 SKU ID */
    private Long skuId;

    /** 活动价（元） */
    private BigDecimal activityPrice;

    /** 生效时间 */
    private LocalDateTime validFrom;

    /** 失效时间 */
    private LocalDateTime validTo;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 备注 */
    private String remark;
}
