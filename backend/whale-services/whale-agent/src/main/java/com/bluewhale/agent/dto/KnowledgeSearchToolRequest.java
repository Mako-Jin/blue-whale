package com.bluewhale.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 智能体只读工具 {@code knowledge_search} 入参。
 *
 * @author BlueWhale
 */
@Data
public class KnowledgeSearchToolRequest {

    /** 检索文本 */
    @NotBlank
    @Size(max = 2000)
    private String query;

    /** 返回条数（可选，默认取检索配置，上限 50） */
    @Min(1)
    @Max(50)
    private Integer topK;

    /** 知识类型过滤（可选：code/doc/sql/config） */
    @Size(max = 8)
    private List<@Size(max = 16) String> kinds;
}
