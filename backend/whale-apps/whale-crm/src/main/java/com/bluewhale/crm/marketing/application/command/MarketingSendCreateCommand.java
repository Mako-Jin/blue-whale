package com.bluewhale.crm.marketing.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建批量触达任务命令。
 *
 * @author BlueWhale
 */
@Data
public class MarketingSendCreateCommand {

    /** 营销活动 ID */
    @NotNull(message = "营销活动不能为空")
    private Long campaignId;

    /** 人群快照 ID */
    @NotNull(message = "人群快照不能为空")
    private Long audienceId;

    /** 触达渠道：EMAIL / SMS / INBOX */
    @NotBlank(message = "触达渠道不能为空")
    private String channel;

    /** 消息模板编码 */
    private String templateCode;

    /** 消息主题 */
    @Size(max = 255, message = "消息主题最多 255 字")
    private String subject;

    /** 内容文档 ID */
    private Long contentRefDocId;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;
}
