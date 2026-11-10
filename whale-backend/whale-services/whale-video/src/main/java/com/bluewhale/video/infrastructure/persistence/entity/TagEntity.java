package com.bluewhale.video.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 视频标签实体，对应表 {@code vd_tag}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vd_tag")
public class TagEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 标签名称 */
    private String name;

    /** 标签类型 */
    private String tagType;

    /** 排序 */
    private Integer sortNo;
}
