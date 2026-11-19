package com.bluewhale.message.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 逻辑消息实体，对应表 {@code msg_record}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("msg_record")
public class MessageRecordPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 业务类型，如 PM_TASK / CONTRACT_EXPIRE */
    private String bizType;

    /** 业务标识 */
    private String bizId;

    /** 模板编码（未使用模板时为空） */
    private String templateCode;

    /** 幂等键（租户内唯一） */
    private String idempotencyKey;

    /** 消息标题 */
    private String title;

    /** 消息正文 */
    private String content;

    /** 模板变量 JSON */
    private String payloadJson;

    /** 渠道列表（大写逗号分隔） */
    private String channels;

    /** 消息状态：PENDING / SENT / DELIVERED / PARTIAL / FAILED / MOCKED / SKIPPED / EXPIRED（与 MessageStatus 枚举一致） */
    private String status;

    /** 回执回调地址（业务方内网端点；空表示不回传回执） */
    private String callbackUrl;

    /** 消息过期时间（超过后不再投递，投递记 EXPIRED） */
    private LocalDateTime expireAt;
}
