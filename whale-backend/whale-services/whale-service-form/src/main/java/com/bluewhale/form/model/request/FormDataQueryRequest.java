package com.bluewhale.form.model.request;

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

    /**
     * 动态字段过滤条件（JSON 数组文本，如 {@code [{"field":"amount","op":"gte","value":100}]}）
     *
     * <p>仅在指定 {@code definitionCode} 时生效；格式非法或字段不在表单结构定义中抛参数错误。</p>
     */
    private String filters;
}
