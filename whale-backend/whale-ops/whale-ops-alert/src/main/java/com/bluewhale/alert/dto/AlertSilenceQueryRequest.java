package com.bluewhale.alert.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 告警静默分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AlertSilenceQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 规则编码（精确匹配） */
    private String ruleCode;

    /** 是否仅查询当前生效中的静默 */
    private Boolean activeOnly;
}
