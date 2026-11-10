package com.bluewhale.twin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 告警外发投递记录实体，对应表 {@code twin_alert_dispatch}。
 *
 * <p>每次外发尝试落一行：webhook 按地址逐条记录，站内信按一次发送记录。
 * 投递失败不影响告警落库与页面可见性，失败原因留在本表供排查与前端展示。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("twin_alert_dispatch")
public class TwinAlertDispatch extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID（行级隔离） */
    private Long tenantId;

    /** 告警ID（恢复类事件为空） */
    private Long alertId;

    /** 所属场景ID */
    private Long sceneId;

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

    /** 站内信消息ID（whale-message 返回） */
    private Long messageId;

    /** 失败原因（截断 512） */
    private String errorMsg;
}
