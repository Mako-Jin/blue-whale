package com.bluewhale.form.model.request;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单定义分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FormQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 表单名称（模糊匹配） */
    private String name;

    /** 表单编码（精确匹配） */
    private String code;

    /** 状态：1 正常 / 0 禁用（精确匹配） */
    private Integer status;

    /** 表单分类（精确匹配；空白不过滤） */
    private String category;
}
