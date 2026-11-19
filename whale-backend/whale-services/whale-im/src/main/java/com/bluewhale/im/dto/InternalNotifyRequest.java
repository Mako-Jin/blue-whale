package com.bluewhale.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 内网站内通知请求（服务间调用，如项目管理指派/评论/流转提醒）。
 *
 * @author BlueWhale
 */
@Data
public class InternalNotifyRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 接收人用户ID列表（必填，单次最多 100 人） */
    @NotEmpty(message = "接收人不能为空")
    @Size(max = 100, message = "单次通知接收人不能超过 100 人")
    private List<Long> userIds;

    /** 通知标题（必填，最长 128） */
    @NotBlank(message = "通知标题不能为空")
    @Size(max = 128, message = "通知标题长度不能超过 128")
    private String title;

    /** 通知正文（可选，最长 2000） */
    @Size(max = 2000, message = "通知正文长度不能超过 2000")
    private String content;

    /** 业务类型（可选，最长 32，用于幂等键与排障） */
    @Size(max = 32, message = "业务类型长度不能超过 32")
    private String bizType;

    /** 业务标识（可选，最长 64，用于幂等键与排障） */
    @Size(max = 64, message = "业务标识长度不能超过 64")
    private String bizId;

    /** 客户端消息ID（可选，最长 64；为空时由服务端生成，重复提交按会话幂等） */
    @Size(max = 64, message = "客户端消息ID长度不能超过 64")
    private String clientMsgId;

    /** 租户ID（消息服务调用时传入，用于回执上报；其它调用方可为空） */
    private Long tenantId;
}
