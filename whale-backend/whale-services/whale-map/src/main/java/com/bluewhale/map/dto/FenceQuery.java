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
    @Size(max = 64, message = "{validation.map.fence-query.name-max}")
    private String name;

    /** 业务类型（可空） */
    private String bizType;

    /** 业务对象ID（可空） */
    private String bizId;

    /** 是否启用（可空，空则不限） */
    private Integer enabled;

    /** 页码 */
    @Min(value = 1, message = "{validation.map.fence-query.page-min}")
    private Integer page = 1;

    /** 每页条数 */
    @Min(value = 1, message = "{validation.map.fence-query.size-min}")
    @Max(value = 200, message = "{validation.map.fence-query.size-max}")
    private Integer size = 20;
}
