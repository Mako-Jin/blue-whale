package com.bluewhale.map.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 围栏分页查询条件。
 *
 * @author BlueWhale
 */
@Data
public class FenceQuery {

    /** 名称模糊匹配 */
    @Size(max = 64, message = "名称关键字不能超过 64 字")
    private String name;

    /** 业务类型（可空） */
    private String bizType;

    /** 业务对象ID（可空） */
    private String bizId;

    /** 是否启用（可空，空则不限） */
    private Integer enabled;

    /** 页码 */
    @Min(value = 1, message = "页码从 1 开始")
    private Integer page = 1;

    /** 每页条数 */
    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 200, message = "每页最多 200 条")
    private Integer size = 20;
}
