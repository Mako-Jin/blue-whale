package com.bluewhale.config.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 配置项分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConfigItemQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字：配置键 / 说明模糊匹配 */
    private String keyword;

    /** 状态（精确匹配）：1 启用 / 0 停用 */
    private Integer status;

    /** 值类型（精确匹配）：STRING / NUMBER / BOOLEAN / JSON */
    private String valueType;
}
