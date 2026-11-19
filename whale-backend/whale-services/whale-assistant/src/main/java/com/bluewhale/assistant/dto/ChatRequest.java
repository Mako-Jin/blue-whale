package com.bluewhale.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 问答请求（会话流式接口，会话ID在路径上）。
 *
 * @author BlueWhale
 */
@Data
public class ChatRequest {

    /** 用户问题 */
    @NotBlank(message = "{validation.assistant.chat.question-required}")
    @Size(max = 2000, message = "{validation.assistant.chat.question-size}")
    private String question;
}
