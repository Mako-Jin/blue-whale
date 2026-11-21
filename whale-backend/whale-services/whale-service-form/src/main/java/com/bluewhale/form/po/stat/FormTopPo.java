package com.bluewhale.form.po.stat;

import lombok.Data;

/**
 * 按表单聚合的提交条数（考功殿概览 T5.7）。
 *
 * <p>同时带出编码与名称：编码是分组键（{@code form_data.definition_code}），
 * 名称来自 {@code form_definition} 的关联——只给编码的话，柱图横轴会变成
 * 一排 {@code expense_claim} 这样的标识符，看的人得先去后台换算成业务名。</p>
 *
 * @author BlueWhale
 */
@Data
public class FormTopPo {

    /** 表单编码；定义被删除或改码时可能为空 */
    private String definitionCode;

    /** 表单名称；定义不存在（历史数据、已删除）时为空，由调用方回落编码 */
    private String formName;

    /** 时间窗内的提交条数 */
    private Long submits;
}
