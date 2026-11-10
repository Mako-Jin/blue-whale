package com.bluewhale.twin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 告警投递记录视图对象（Wave 6.10 M2：场景维度列表）。
 *
 * <p>一行对应一次投递尝试：webhook 按地址逐条，站内信按一次发送。
 * {@code status} 为 {@code SUCCESS / FAILED / SKIPPED}，失败原因供前端直接展示。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwinAlertDispatchVO {

    /** 记录ID（字符串化） */
    private String id;

    /** 告警ID（字符串化，恢复类事件为空） */
    private String alertId;

    /** 场景ID（字符串化） */
    private String sceneId;

    /** 实体状态键 */
    private String stateKey;

    /** 触发级别：warn / critical */
    private String level;

    /** 通道：WEBHOOK / INAPP */
    private String channel;

    /** 投递目标（webhook 地址或接收人概要） */
    private String target;

    /** 结果：SUCCESS / FAILED / SKIPPED */
    private String status;

    /** 站内信消息ID（字符串化，webhook 为空） */
    private String messageId;

    /** 失败原因 */
    private String errorMsg;

    /** 记录时间（ISO-8601） */
    private String createTime;
}
