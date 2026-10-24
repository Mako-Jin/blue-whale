package com.bluewhale.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送消息请求（{@code POST /im/conversations/{id}/messages}）。
 *
 * <p>{@code clientMsgId} 为客户端生成的幂等键：同一会话内重复提交返回已存在消息，
 * 不重复落库、不重复分配序号。</p>
 *
 * @author BlueWhale
 */
@Data
public class SendMessageRequest {

    /** 客户端消息ID（幂等键，最长 64） */
    @NotBlank
    @Size(max = 64)
    private String clientMsgId;

    /** 内容类型：TEXT / MARKDOWN / IMAGE / FILE / CARD */
    @NotBlank
    @Pattern(regexp = "TEXT|MARKDOWN|IMAGE|FILE|CARD")
    private String contentType;

    /** 消息内容（文本原文，或附件/卡片 JSON 引用，最长 16000 字） */
    @NotBlank
    @Size(max = 16000)
    private String content;

    /** 被回复消息ID（可空） */
    private Long replyTo;
}
