package com.bluewhale.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 智能体只读工具 {@code fact_get} 入参。
 *
 * @author BlueWhale
 */
@Data
public class FactGetToolRequest {

    /** 事实类型（可选：SERVICE/ROUTE/PORT/DEPENDENCY/TABLE/COLUMN/COMMIT/ENDPOINT） */
    @Size(max = 32)
    private String type;

    /** 事实键（可选） */
    @Size(max = 255)
    private String key;

    /** 返回条数（可选，默认 50，上限 200） */
    @Min(1)
    @Max(200)
    private Integer limit;
}
