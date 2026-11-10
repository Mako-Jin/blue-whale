package com.bluewhale.crm.sales.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 线索活动追加命令。
 *
 * @author BlueWhale
 */
@Data
public class LeadActivityCreateCommand {

    /** 渠道：CALL / VISIT / EMAIL / IM / WECHAT / OTHER */
    @NotBlank(message = "活动渠道不能为空")
    private String channel;

    /** 活动内容 */
    @NotBlank(message = "活动内容不能为空")
    private String content;

    /** 活动结果 */
    private String result;

    /** 下次行动时间 */
    private LocalDateTime nextActionAt;
}
