package com.bluewhale.oa.integration.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Outbox 事件追加命令。
 *
 * @author BlueWhale
 */
@Data
public class OutboxAppendCommand {

    /** 事件幂等键（租户内唯一） */
    @NotBlank(message = "事件键不能为空")
    @Size(max = 128, message = "事件键长度不能超过 128")
    private String eventKey;

    /** 事件类型 */
    @NotBlank(message = "事件类型不能为空")
    @Size(max = 64, message = "事件类型长度不能超过 64")
    private String eventType;

    /** 聚合类型 */
    @NotBlank(message = "聚合类型不能为空")
    @Size(max = 64, message = "聚合类型长度不能超过 64")
    private String aggregateType;

    /** 聚合ID */
    @NotBlank(message = "聚合ID不能为空")
    @Size(max = 64, message = "聚合ID长度不能超过 64")
    private String aggregateId;

    /** 事件体（JSON） */
    private String payload;
}
