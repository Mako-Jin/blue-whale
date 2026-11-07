package com.bluewhale.mall.channel.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道视图。
 *
 * @author BlueWhale
 */
@Data
public class ChannelView {

    /** 渠道 ID */
    private Long id;

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

    /** 运费模板 ID（空 = 不收运费） */
    private Long freightTemplateId;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
