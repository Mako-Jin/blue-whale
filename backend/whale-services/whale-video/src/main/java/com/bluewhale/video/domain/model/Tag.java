package com.bluewhale.video.domain.model;

import lombok.Data;

/**
 * 视频标签（名称 + 类型在租户内唯一）。
 *
 * @author BlueWhale
 */
@Data
public class Tag {

    /** 主键 */
    private Long id;

    /** 标签名称 */
    private String name;

    /** 标签类型 */
    private TagType tagType;

    /** 排序 */
    private Integer sortNo;
}
