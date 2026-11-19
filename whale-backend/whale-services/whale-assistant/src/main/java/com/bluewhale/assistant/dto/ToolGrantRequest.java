package com.bluewhale.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 助手工具授权请求：整表覆盖式设置白名单。
 *
 * @author BlueWhale
 */
@Data
public class ToolGrantRequest {

    /** 已授权工具编码全集（覆盖式；空列表 = 清空白名单） */
    @NotBlank(message = "{validation.assistant.tool-grant.tool-codes-required}")
    private List<String> toolCodes;
}
