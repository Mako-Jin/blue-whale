package com.bluewhale.form.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单附件引用实体，对应表 {@code form_attachment_ref}（附件反查索引）。
 *
 * <p>只记录「表单数据 → 附件」的引用关系，不存文件本身；提交/修改/导入时同步维护，
 * 删除表单数据时清理。用于「某附件被哪些表单数据引用」的反查。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("form_attachment_ref")
public class FormAttachmentRefPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 表单数据ID */
    private Long dataId;

    /** 表单编码（冗余，便于按表单查询） */
    private String definitionCode;

    /** 附件字段编码 */
    private String fieldKey;

    /** 文件ID */
    private Long attachmentId;
}
