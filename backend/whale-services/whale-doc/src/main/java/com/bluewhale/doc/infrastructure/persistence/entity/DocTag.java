package com.bluewhale.doc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 空间标签实体，对应表 {@code doc_tag}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_tag")
public class DocTag extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 空间ID */
    private Long spaceId;

    /** 标签名称（空间内唯一） */
    private String name;

    /** 标签颜色（十六进制） */
    private String color;
}
