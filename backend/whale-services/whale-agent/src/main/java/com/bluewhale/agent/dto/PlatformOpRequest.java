package com.bluewhale.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台写操作创建请求（Python 工具回调 {@code POST /agent/tools/platform-op}）。
 *
 * @author BlueWhale
 */
@Data
public class PlatformOpRequest {

    /** 操作名，如 {@code form.definition.create} */
    @NotBlank(message = "操作名不能为空")
    private String operation;

    /** 操作参数（按目录字段白名单过滤） */
    private Map<String, Object> params = new LinkedHashMap<>();

    /** 来源智能体会话ID（可空） */
    private Long conversationId;
}
