package com.bluewhale.party.integration.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 通知留痕实体，对应表 {@code pty_notify_log}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_notify_log")
public class NotifyLogEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 通知场景 */
    private String scene;

    /** 幂等键 */
    private String idempotencyKey;

    /** 业务类型 */
    private String bizType;

    /** 业务 ID */
    private Long bizId;

    /** 接收人用户 ID */
    private Long userId;

    /** 模板编码 */
    private String templateCode;

    /** 标题 */
    private String title;

    /** 渠道 */
    private String channel;

    /** 投递状态 */
    private String sendStatus;

    /** 失败原因 */
    private String failReason;

    /** 投递时间 */
    private LocalDateTime sentAt;
}
