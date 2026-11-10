package com.bluewhale.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 指令视图对象。
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandVO {

    /** 指令ID（字符串化） */
    private String id;

    /** 目标设备ID（字符串化） */
    private String deviceId;

    /** 服务标识符 */
    private String identifier;

    /** 指令参数（原样回显） */
    private Object params;

    /** 消息ID（幂等键；设备回执按此关联） */
    private String messageId;

    /** 状态：pending/sent/success/failed/timeout */
    private String status;

    /** 设备回执（原样回显，可空） */
    private Object reply;

    /** 下发时间（ISO-8601，可空） */
    private String issuedTime;

    /** 回执时间（ISO-8601，可空） */
    private String replyTime;

    /** 创建时间（ISO-8601） */
    private String createTime;
}
