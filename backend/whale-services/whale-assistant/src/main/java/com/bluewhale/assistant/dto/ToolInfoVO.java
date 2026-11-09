package com.bluewhale.assistant.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 工具注册表条目视图（whale-ai {@code GET /v1/tools} 的透传描述）。
 *
 * @author BlueWhale
 */
@Getter
@Builder
public class ToolInfoVO {

    /** 工具编码（asst_tool_grant.tool_code 须取自本清单） */
    private String name;

    /** 工具描述 */
    private String description;

    /** 风险级（L0/L1/L2） */
    private String riskLevel;

    /** 工具执行所需权限码 */
    private String permission;
}
