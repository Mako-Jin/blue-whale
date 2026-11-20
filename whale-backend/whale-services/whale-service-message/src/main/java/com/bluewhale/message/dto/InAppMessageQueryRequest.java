package com.bluewhale.message.dto;

import com.bluewhale.common.core.query.PageQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内信收件箱分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InAppMessageQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 已读标记：1 已读 / 0 未读；为空表示全部 */
    @Min(value = 0, message = "{validation.message.inapp.read-flag-range}")
    @Max(value = 1, message = "{validation.message.inapp.read-flag-range}")
    private Integer readFlag;
}
