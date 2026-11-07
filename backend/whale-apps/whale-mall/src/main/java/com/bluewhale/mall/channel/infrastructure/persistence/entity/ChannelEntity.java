package com.bluewhale.mall.channel.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 渠道实体，对应表 {@code mall_channel}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_channel")
public class ChannelEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 渠道编码 */
    private String channelCode;

    /** 渠道名称 */
    private String channelName;

    /** 形态 */
    private String mode;

    /** 结算方式 */
    private String settlementType;

    /** 金额口径 */
    private String currencyType;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
