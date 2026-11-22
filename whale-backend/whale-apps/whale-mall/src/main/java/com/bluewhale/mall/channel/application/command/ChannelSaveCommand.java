package com.bluewhale.mall.channel.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 渠道保存命令。
 *
 * @author BlueWhale
 */
@Data
public class ChannelSaveCommand {

    /** 渠道编码（租户内唯一） */
    @NotBlank(message = "{validation.mall.channel.channel-code-required}")
    @Size(max = 64, message = "{validation.mall.channel.channel-code-size}")
    private String channelCode;

    /** 渠道名称 */
    @NotBlank(message = "{validation.mall.channel.channel-name-required}")
    @Size(max = 128, message = "{validation.mall.channel.channel-name-size}")
    private String channelName;

    /** 形态：RETAIL / B2B / WELFARE */
    @NotBlank(message = "{validation.mall.channel.mode-required}")
    private String mode;

    /** 结算方式：ONLINE / CREDIT / POINTS */
    @NotBlank(message = "{validation.mall.channel.settlement-type-required}")
    private String settlementType;

    /** 运费模板 ID（可空 = 不收运费；积分商城忽略） */
    private Long freightTemplateId;

    /** 备注 */
    @Size(max = 255, message = "{validation.mall.channel.remark-size}")
    private String remark;
}
