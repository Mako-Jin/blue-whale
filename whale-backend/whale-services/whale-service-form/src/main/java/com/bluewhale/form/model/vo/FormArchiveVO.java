package com.bluewhale.form.model.vo;

import com.bluewhale.form.support.FormField;
import lombok.Data;

import java.util.List;

/**
 * 表单归档详情视图对象（档案同步内网接口契约）。
 *
 * <p>同时返回表单数据（含结构化字段）与字段定义（含 {@code attachment} / {@code subtable} 结构），
 * 供档案服务递归提取附件并生成带标签的快照。</p>
 *
 * @author BlueWhale
 */
@Data
public class FormArchiveVO {

    /** 表单数据（含结构化 {@code fields}） */
    private FormDataVO data;

    /** 字段定义（含子表结构） */
    private List<FormField> fieldDefinitions;
}
