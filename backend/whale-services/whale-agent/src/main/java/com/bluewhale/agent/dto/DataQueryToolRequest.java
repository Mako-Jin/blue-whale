package com.bluewhale.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 智能体只读数据工具 {@code data_query} 入参。
 *
 * @author BlueWhale
 */
@Data
public class DataQueryToolRequest {

    /** 单条 SELECT（仅可访问白名单视图，服务端强制 LIMIT 与脱敏） */
    @NotBlank
    @Size(max = 4000)
    private String sql;
}
