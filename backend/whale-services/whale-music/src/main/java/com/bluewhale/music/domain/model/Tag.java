package com.bluewhale.music.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签领域模型。
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

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
