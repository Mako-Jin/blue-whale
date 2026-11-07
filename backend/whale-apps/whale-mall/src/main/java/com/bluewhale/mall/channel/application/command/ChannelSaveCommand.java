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
    @NotBlank(message = "渠道编码不能为空")
    @Size(max = 64, message = "渠道编码不得超过 64 字符")
    private String channelCode;

    /** 渠道名称 */
    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 128, message = "渠道名称不得超过 128 字符")
    private String channelName;

    /** 形态：RETAIL / B2B / WELFARE */
    @NotBlank(message = "必须指定商城形态")
    private String mode;

    /** 结算方式：ONLINE / CREDIT / POINTS */
    @NotBlank(message = "必须指定结算方式")
    private String settlementType;

    /** 运费模板 ID（可空 = 不收运费；积分商城忽略） */
    private Long freightTemplateId;

    /** 备注 */
    @Size(max = 255, message = "备注不得超过 255 字符")
    private String remark;
}
