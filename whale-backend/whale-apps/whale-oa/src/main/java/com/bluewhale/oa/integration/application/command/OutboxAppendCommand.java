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
    @NotBlank(message = "{validation.oa.outbox-append.event-key-required}")
    @Size(max = 128, message = "{validation.oa.outbox-append.event-key-size}")
    private String eventKey;

    /** 事件类型 */
    @NotBlank(message = "{validation.oa.outbox-append.event-type-required}")
    @Size(max = 64, message = "{validation.oa.outbox-append.event-type-size}")
    private String eventType;

    /** 聚合类型 */
    @NotBlank(message = "{validation.oa.outbox-append.aggregate-type-required}")
    @Size(max = 64, message = "{validation.oa.outbox-append.aggregate-type-size}")
    private String aggregateType;

    /** 聚合ID */
    @NotBlank(message = "{validation.oa.outbox-append.aggregate-id-required}")
    @Size(max = 64, message = "{validation.oa.outbox-append.aggregate-id-size}")
    private String aggregateId;

    /** 事件体（JSON） */
    private String payload;
}
