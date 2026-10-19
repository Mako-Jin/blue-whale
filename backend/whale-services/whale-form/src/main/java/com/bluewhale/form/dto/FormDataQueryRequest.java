package com.bluewhale.form.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单数据分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FormDataQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 表单定义编码（精确匹配） */
    private String definitionCode;

    /** 业务键（精确匹配） */
    private String bizKey;

    /** 提交人账号（精确匹配） */
    private String submitter;
}
