package com.bluewhale.form.model.dto;

import lombok.Data;

/**
 * 动态字段过滤条件（分页/导出查询使用）。
 *
 * <p>由查询参数 {@code filters}（JSON 数组文本）反序列化得到，例如：
 * {@code [{"field":"amount","op":"gte","value":100},{"field":"stage","op":"in","value":["new","done"]}]}。
 * {@code op} 缺省为 {@code eq}；{@code in}/{@code between} 的 {@code value} 为数组。</p>
 *
 * @author BlueWhale
 */
@Data
public class FormDataFilter {

    /** 动态字段 key（物理表列名，须在表单结构定义中） */
    private String field;

    /** 操作符：eq / ne / gt / gte / lt / lte / like / in / between / isNull / notNull */
    private String op;

    /** 比较值（in/between 为数组；isNull/notNull 忽略） */
    private Object value;
}
