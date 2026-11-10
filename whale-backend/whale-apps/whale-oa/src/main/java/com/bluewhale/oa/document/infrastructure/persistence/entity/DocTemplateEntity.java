package com.bluewhale.oa.document.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公文模板实体，对应表 {@code oa_doc_template}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_doc_template")
public class DocTemplateEntity extends BaseEntity {

    private String code;

    private String name;

    private String docType;

    private Integer version;

    private String status;

    private String headerText;

    private String footerText;

    private String bodyTemplate;

    private String remark;
}
