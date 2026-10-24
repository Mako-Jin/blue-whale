package com.bluewhale.agent.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 智能体只读工具 {@code doc_read} 入参。
 *
 * @author BlueWhale
 */
@Data
public class DocReadToolRequest {

    /** 仓库相对路径 */
    @NotBlank
    @Size(max = 512)
    private String path;

    /** 起始行（可选，默认 1） */
    @Min(1)
    private Integer startLine;

    /** 结束行（可选，默认文件末尾） */
    @Min(1)
    private Integer endLine;
}
