package com.bluewhale.mall.catalog.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 限时特价活动。
 *
 * <p>活动按 SKU 一条记录：生效时间窗内且状态启用时，下单计价取
 * 「活动价与渠道价目孰低」，活动命中明细（活动 ID/名称/让利前单价）
 * 全部快照进订单行——活动事后停用或改价，历史订单必须还能解释
 * 「这行为什么是这个价」。</p>
 *
 * <p>不做活动库存与限购：限时特价是<b>价格覆盖</b>而不是配额分配，
 * 配额（秒杀库存/限购件数）是另一套模型，混进来会把价格计算搞成库存账。</p>
 *
 * @author BlueWhale
 */
@Data
public class Activity {

    /** 编码最大长度 */
    public static final int CODE_MAX_LENGTH = 64;

    /** 名称最大长度 */
    public static final int NAME_MAX_LENGTH = 128;

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 255;

    /** 主键 */
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

    /** 状态 */
    private ActivityStatus status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验并规整活动字段。
     */
    public void validate() {
        validateCode();
        validateNameAndRemark();
        validateSkuAndPrice();
        validatePeriod();
        if (status == null) {
            status = ActivityStatus.ACTIVE;
        }
    }

    /**
     * 校验并规整活动编码。
     */
    private void validateCode() {
        if (activityCode == null || activityCode.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.ACTIVITY_INVALID, "活动编码不能为空");
        }
        activityCode = activityCode.trim().toUpperCase(Locale.ROOT);
        if (activityCode.length() > CODE_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.ACTIVITY_INVALID,
                    "活动编码不得超过 " + CODE_MAX_LENGTH + " 字符");
        }
    }

    /**
     * 校验并规整活动名称与备注。
     */
    private void validateNameAndRemark() {
        if (activityName == null || activityName.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.ACTIVITY_INVALID, "活动名称不能为空");
        }
        activityName = activityName.trim();
        if (activityName.length() > NAME_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.ACTIVITY_INVALID,
                    "活动名称不得超过 " + NAME_MAX_LENGTH + " 字符");
        }
        if (remark != null && remark.length() > REMARK_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.ACTIVITY_INVALID,
                    "备注不得超过 " + REMARK_MAX_LENGTH + " 字符");
        }
    }

    /**
     * 校验 SKU 与活动价。
     */
    private void validateSkuAndPrice() {
        if (skuId == null || skuId <= 0) {
            throw MallErrorCode.exception(MallErrorCode.ACTIVITY_INVALID, "活动必须绑定有效 SKU");
        }
        if (activityPrice == null || activityPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw MallErrorCode.exception(MallErrorCode.ACTIVITY_INVALID, "活动价必须大于 0");
        }
        activityPrice = activityPrice.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 校验时间窗。
     */
    private void validatePeriod() {
        if (validFrom == null || validTo == null) {
            throw MallErrorCode.exception(MallErrorCode.ACTIVITY_INVALID, "必须指定生效与失效时间");
        }
        if (!validTo.isAfter(validFrom)) {
            throw MallErrorCode.exception(MallErrorCode.ACTIVITY_INVALID, "失效时间必须晚于生效时间");
        }
    }

    /**
     * 是否在指定时刻对指定渠道命中（时间窗 + 状态 + 渠道）。
     *
     * <p>活动价还必须低于当前成交价才真正生效，但「孰低」是计价层的职责
     * （需要拿到价目），本方法只判活动自身的适用性。</p>
     *
     * @param now       当前时间
     * @param channelId 订单渠道 ID
     * @return 命中返回 true
     */
    public boolean hits(LocalDateTime now, Long channelId) {
        if (status != null && status != ActivityStatus.ACTIVE) {
            return false;
        }
        if (now == null) {
            return false;
        }
        if (now.isBefore(validFrom) || now.isAfter(validTo)) {
            return false;
        }
        return this.channelId == null || this.channelId.equals(channelId);
    }
}
