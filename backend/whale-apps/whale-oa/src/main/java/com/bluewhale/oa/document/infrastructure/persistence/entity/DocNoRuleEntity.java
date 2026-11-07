package com.bluewhale.oa.document.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文号规则实体，对应表 {@code oa_doc_no_rule}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_doc_no_rule")
public class DocNoRuleEntity extends BaseEntity {

    private String code;

    private String name;

    private String docType;

    private String prefix;

    private String periodFormat;

    private Integer serialLength;

    private String separator;

    private Integer enabled;

    private String remark;
}
