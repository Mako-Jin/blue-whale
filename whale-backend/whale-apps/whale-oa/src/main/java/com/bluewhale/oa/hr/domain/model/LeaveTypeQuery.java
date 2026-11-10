package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 假期类型分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeaveTypeQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 编码（模糊匹配） */
    private String code;

    /** 名称（模糊匹配） */
    private String name;

    /** 是否启用 */
    private Boolean enabled;
}
