package com.bluewhale.music.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 标签实体，对应表 {@code mu_tag}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mu_tag")
public class TagEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 标签名称 */
    private String name;

    /** 标签类型：GENRE / LANGUAGE / MOOD */
    private String tagType;

    /** 排序 */
    private Integer sortNo;
}
