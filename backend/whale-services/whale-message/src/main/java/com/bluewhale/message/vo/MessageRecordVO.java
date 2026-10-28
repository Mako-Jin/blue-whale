package com.bluewhale.message.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息记录视图。
 *
 * @author BlueWhale
 */
@Data
public class MessageRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息ID */
    private Long id;

    /** 业务类型 */
    private String bizType;

    /** 业务标识 */
    private String bizId;

    /** 模板编码 */
    private String templateCode;

    /** 幂等键 */
    private String idempotencyKey;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 模板变量 JSON */
    private String payloadJson;

    /** 渠道列表 */
    private String channels;

    /** 消息状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 投递明细（仅详情接口返回） */
    private List<MessageDeliveryVO> deliveries;
}
