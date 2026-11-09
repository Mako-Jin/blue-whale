package com.bluewhale.mall.catalog.application.command;

import com.bluewhale.mall.catalog.domain.model.Activity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动保存命令。
 *
 * @author BlueWhale
 */
@Data
public class ActivitySaveCommand {

    /** 主键（新建为空） */
    private Long id;

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

    /** 状态：ACTIVE / DISABLED（空默认 ACTIVE） */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 转领域对象。
     *
     * @return 活动领域对象
     */
    public Activity toDomain() {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setActivityCode(activityCode);
        activity.setActivityName(activityName);
        activity.setChannelId(channelId);
        activity.setSkuId(skuId);
        activity.setActivityPrice(activityPrice);
        activity.setValidFrom(validFrom);
        activity.setValidTo(validTo);
        activity.setStatus(com.bluewhale.mall.catalog.domain.model.ActivityStatus.parse(status));
        activity.setRemark(remark);
        return activity;
    }
}
