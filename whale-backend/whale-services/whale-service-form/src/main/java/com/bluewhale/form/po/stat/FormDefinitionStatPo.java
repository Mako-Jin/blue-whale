package com.bluewhale.form.po.stat;

import lombok.Data;

/**
 * 表单定义数量统计（考功殿概览 T5.7）。
 *
 * <p>一张 KPI 要同时说清"一共有几个表单"和"其中几个还开着"，
 * 所以两个数一次查回来，不为了少一次往返让屏风显示"启用 N 个"却说不出总数。</p>
 *
 * @author BlueWhale
 */
@Data
public class FormDefinitionStatPo {

    /** 在册表单定义数（未逻辑删除） */
    private Long definitions;

    /** 其中状态正常的（{@code status = 1}） */
    private Long enabled;
}
