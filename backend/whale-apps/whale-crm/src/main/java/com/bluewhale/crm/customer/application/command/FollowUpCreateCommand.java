package com.bluewhale.crm.customer.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 跟进记录新增命令（append-only）。
 *
 * @author BlueWhale
 */
@Data
public class FollowUpCreateCommand {

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    private Long customerId;

    /** 商机 ID */
    private Long opportunityId;

    /** 渠道 */
    @NotBlank(message = "跟进渠道不能为空")
    private String channel;

    /** 内容 */
    @NotBlank(message = "跟进内容不能为空")
    private String content;

    /** 结果 */
    private String result;

    /** 下次动作 */
    private String nextAction;

    /** 下次动作时间 */
    private LocalDateTime nextActionAt;
}
