package com.bluewhale.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 助手定义保存请求（创建与更新共用；code 创建后不可改）。
 *
 * @author BlueWhale
 */
@Data
public class DefinitionSaveRequest {

    /** 助手编码（租户内唯一；创建必填，更新忽略） */
    @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$", message = "{validation.assistant.definition.code-pattern}")
    private String code;

    /** 助手名称 */
    @NotBlank(message = "{validation.assistant.definition.name-required}")
    @Size(max = 128, message = "{validation.assistant.definition.name-size}")
    private String name;

    /** 头像（whale-file 附件URL，可空） */
    @Size(max = 512, message = "{validation.assistant.definition.avatar-size}")
    private String avatar;

    /** 助手简介（可空） */
    @Size(max = 512, message = "{validation.assistant.definition.description-size}")
    private String description;

    /** 系统提示词（可空；上限 8000 字符） */
    @Size(max = 8000, message = "{validation.assistant.definition.system-prompt-size}")
    private String systemPrompt;

    /** 模型参数（仅允许 model/temperature/maxTokens 三键，可空） */
    private Map<String, Object> modelParams;

    /** 知识范围（透传 whale-agent 检索 scope，可空=全部） */
    @Size(max = 255, message = "{validation.assistant.definition.knowledge-scope-size}")
    private String knowledgeScope;
}
