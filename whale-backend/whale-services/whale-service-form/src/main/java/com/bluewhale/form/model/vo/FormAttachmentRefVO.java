package com.bluewhale.form.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 表单附件引用视图对象（附件反查结果，字段名为冻结的 JSON 契约）。
 *
 * @author BlueWhale
 */
@Data
public class FormAttachmentRefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 表单数据ID */
    private Long dataId;

    /** 表单编码 */
    private String definitionCode;

    /** 附件字段编码 */
    private String fieldKey;

    /** 文件ID */
    private Long attachmentId;
}
