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
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题长度不能超过 2000 字")
    private String question;
}
